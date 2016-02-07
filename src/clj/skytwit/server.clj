(ns skytwit.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [twitter.oauth :as oauth]
            [twitter.callbacks :as cb]
            [twitter.callbacks.handlers :as twh]
            [twitter.api :as tapi]
            [twitter.api.restful :as tr]
            [twitter.api.streaming :as ts]
            [cheshire.core :as json]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit
             :refer [sente-web-server-adapter]]
            [clojure.core.async :as async :refer [go go-loop >! <!]])
  (:import (twitter.callbacks.protocols AsyncStreamingCallback
                                        SyncSingleCallback))
  (:gen-class))

(defn get-credentials-from-env
  []
  (oauth/make-oauth-creds (env :oauth-consumer-key)
                          (env :oauth-consumer-secret)
                          (env :oauth-app-key)
                          (env :oauth-app-secret)))

;; Taken from Sente README
(let [{:keys [ch-recv
              send-fn
              ajax-post-fn
              ajax-get-or-ws-handshake-fn
              connected-uids]}
            (sente/make-channel-socket!
              sente-web-server-adapter
              {:user-id-fn (fn [& args] 1)})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(defn get-user-profile
  [creds screen-name]
  (try
    (let [user (-> (tr/users-show :oauth-creds creds
                                  :params {:screen-name "InternetRadio"})
                   :body)]
      {:name (:screen_name user)
       :id (:id user)
       :number-of-tweets (:statuses_count user)
       :last-tweet-id (-> user :status :id)})
    (catch Exception _ nil)))

(defn format-tweet
  [t uid]
  {:user uid
   :id (:id t)
   :tags (->> t :entities :hashtags (mapv :text))})

(defn get-tweets-before-tweet-id
  [creds screen-name id]
  (let [get-raw-tweets
        (fn rec []
          (tr/statuses-user-timeline
            :oauth-creds creds
            :params {:screen-name screen-name
                     :max-id id
                     :trim-user true
                     :exclude-replies true
                     :count 200}
            :callbacks (SyncSingleCallback.
                         twh/response-return-body
                         (fn [resp]
                           ;; TODO: Proper logging
                           (.. System out
                               (println (pr-str [:failure resp])))
                           (if (twh/rate-limit-error? @(:status resp))
                             (do (Thread/sleep (60 * 1000))
                                 (rec))
                             (twh/response-throw-error resp)))
                         twh/exception-rethrow)))
        uid (:id (get-user-profile creds screen-name))
        tweets (->> (get-raw-tweets)
                    (map #(format-tweet % uid)))]
    (if (= id (:id (first tweets)))
      (rest tweets)
      tweets)))

(defn put-old-tweets-on-channel
  [creds screen-name chan]
  (let [max-id (-> (get-user-profile creds screen-name) :last-tweet-id inc)
        end? (atom false)]
    (go-loop [last-id max-id]
             (when-not @end?
               (let [next-batch (get-tweets-before-tweet-id
                                  creds screen-name last-id)]
                 (when (seq next-batch)
                   (>! chan next-batch)
                   (recur (:id (last next-batch)))))))
    (fn [] (reset! end? true))))

(defn put-new-tweets-on-channel
  [creds screen-name chan]
  (let [w (java.io.PipedWriter.)
        r (java.io.PipedReader. w)
        end? (atom false)
        id (:id (get-user-profile creds screen-name))
        s (ts/statuses-filter :params {:follow id}
                              :oauth-creds creds
                              :callbacks (AsyncStreamingCallback.
                                           (fn [_ body]
                                             (io/copy (.toByteArray body) w))
                                           twh/response-throw-error
                                           twh/exception-rethrow))
        f (go-loop [s (json/parsed-seq r true)]
                   (when-not @end?
                     (>! chan [(format-tweet (first s) id)])
                     (recur (rest s))))]
    ;; TODO: There may be a small memory leak here: if statuses-filter
    ;;       is stopped in the middle of a JSON string, the lazy seq may
    ;;       be blocked and the go-block may not get GC'd.
    (fn []
      ((:cancel (meta s)))
      (reset! end? true))))

(defn start-listening-on-user!
  [creds screen-name at]
  (let [ch (async/chan)
        uid (:id (get-user-profile creds screen-name))
        stop-old (put-old-tweets-on-channel creds screen-name ch)
        stop-new (put-new-tweets-on-channel creds screen-name ch)
        stop-fn (fn [] (stop-old) (stop-new))]
    (if-let [stop-previous (:stop-fn @at)] (stop-previous))
    (reset! at {:user-name screen-name
                :user-id uid
                :stop-fn stop-fn
                :tweets []})
    (go-loop []
             (when-let [tweets (<! ch)]
               (->> tweets
                    (filter #(= (:user %) uid))
                    (swap! at update :tweets concat))))))

(defroutes routes
  (GET "/" _
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (resources "/"))

(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip))

(defn start-ws-server
  [creds]
  (let [state (atom {})]
    (go-loop
      []
      (let [{[t b] :event} (<! ch-chsk)]
        (case t
          :post/change (start-listening-on-user! creds (:name b) state)
          :get/update (chsk-send!
                        1 [:data/full
                           (select-keys @state [:user-name :tweets])])
          ;; TODO: better logging
          (prn [:unhandled [t b]])))
      (recur))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))
        creds (get-credentials-from-env)]
    (start-ws-server creds)
    (run-server http-handler {:port port :join? false})))
