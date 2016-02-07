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
  (atom {:user-name "placeholder"
         :tweets [{:tags ["a" "b"]}
                  {:tags ["a"]}]}))

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
                                (mapcat :tags)
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

(go-loop []
         (let [e (:event (<! ch-chsk))]
           (when (= :chsk/recv (first e))
             (let [[t b] (second e)]
               (condp = t
                 :data/full (reset! app-state b)
                 ;; TODO: better logging
                 (prn [:unhandled [t b]])))))
         (recur))

(om/root
 root-component
 app-state
 {:target (. js/document (getElementById "app"))})
