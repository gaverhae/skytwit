(ns skytwit.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            (taoensso.sente :as sente :refer [cb-success?])))

(enable-console-print!)

;; From Sente README
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(defonce app-state
  (atom {:user-name "gaverhae"
         :number-of-tweets 3
         :tweets [{:hashtags ["a" "b"]}
                  {:hashtags ["a"]}]}))

(go-loop []
         (println (:?data (<! ch-chsk)))
         (recur))

(defn root-component [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (dom/h1 nil (:user-name app))
               (dom/pre nil (pr-str (->> (:tweets app)
                                         (mapcat :hashtags)
                                         frequencies)))
               (dom/pre nil (pr-str app))))))

(om/root
 root-component
 app-state
 {:target (. js/document (getElementById "app"))})
