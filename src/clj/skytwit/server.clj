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
             :refer [sente-web-server-adapter]])
  (:import (twitter.callbacks.protocols AsyncStreamingCallback))
  (:gen-class))

(def credentials (oauth/make-oauth-creds (env :oauth-consumer-key)
                                         (env :oauth-consumer-secret)
                                         (env :oauth-app-key)
                                         (env :oauth-app-secret)))

(comment
(->> (tr/statuses-user-timeline
       :oauth-creds credentials
       :params {:screen-name "AdamJWynne"})
     :body
     (map (comp :hashtags :entities))
     (remove empty?)
     (mapcat #(map :text %))
     frequencies)
)

(comment
  (def collected (atom []))

  (def ctx (tapi/make-api-context "https" "stream.twitter.com" "1.1"))

  (let [w (java.io.PipedWriter.)
        r (java.io.PipedReader. w)]
    (ts/statuses-filter :params {:track "InternetRadio"}
                        :api-context ctx
                        :oauth-creds credentials
                        :callbacks (AsyncStreamingCallback.
                                     (fn [_ body]
                                       (io/copy (.toByteArray body) w))
                                     (comp println twh/response-return-everything)
                                     twh/exception-print))
    (future
      (->> (json/parsed-seq r true)
           (map (fn [j]
                  (swap! collected conj j)))
           doall)))

  (->> collected deref
       (map (comp :hashtags :entities))
       (mapcat #(map :text %))
       frequencies)
  )

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

(comment
(chsk-send! 1 {:text "hello"})
)

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

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-server http-handler {:port port :join? false})))
