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

(defn handle-change
  [e owner {:keys [text]}]
  (om/set-state! owner :text (.. e -target -value)))

(defn send-twitter-handle
  [n]
  (chsk-send! [:post/change {:name n}]))

(defn input-field
  [data owner]
  (reify
    om/IInitState
    (init-state [_] {:text ""})
    om/IRenderState
    (render-state [this state]
      (dom/div nil
               (dom/h2 nil "Enter a twitter handle")
               (dom/div nil
                        (dom/input
                          #js {:type "text" :ref "twitter-handle"
                               :value (:text state)
                               :onChange #(handle-change % owner state)})
                        (dom/button #js {:onClick #(send-twitter-handle (:text state))}
                                    "Change handle")))))) 

(defn statistics
  [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/pre nil (pr-str (->> (:tweets app)
                                (mapcat :hashtags)
                                frequencies))))))

(defn refresh
  [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/button #js {:onClick #(chsk-send! [:get/update {}])}
                  "Refresh data"))))

(defn root-component [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (dom/h1 nil (:user-name app))
               (om/build input-field app)
               (om/build statistics app)
               (om/build refresh app)))))

(om/root
 root-component
 app-state
 {:target (. js/document (getElementById "app"))})
