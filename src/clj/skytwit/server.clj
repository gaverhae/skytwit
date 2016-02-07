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
            [cheshire.core :as json])
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

(defroutes routes
  (GET "/" _
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (resources "/"))

(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-server http-handler {:port port :join? false})))
