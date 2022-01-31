(ns ^:figwheel-hooks racer-pacer.core
  (:require [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.string :as gstring]
            [goog.string.format]))

;(def pace-data (r/atom {:minutes 4 :seconds 5}))
(defonce pace-data (r/atom 275))

(def events
  [{:distance/km 5}
   {:distance/km 10}
   {:distance/km 21.0975
    :name "Half marathon"}
   {:distance/km 42.195
    :name "Marathon"}])

(defn seconds->pace [secs]
  (let [hours (quot secs 3600)
        minutes (quot (- secs (* hours 3600)) 60)
        seconds (- secs (* hours 3600) (* minutes 60))]
    {:hours hours
     :minutes minutes
     :seconds seconds}))

(defn show-pace [secs]
  (let [p (seconds->pace secs)]
    (gstring/format "%2d:%02d" (:minutes p) (:seconds p))))

(defn show-time [secs]
  (let [p (seconds->pace secs)]
    (gstring/format "%d:%02d:%02d" (:hours p) (:minutes p) (:seconds p))))

(defn pace->seconds [{:keys [minutes seconds]}]
  (+ seconds (* 60 minutes)))

(defn pace-slider [value min-value max-value]
  [:div
    [:label (str "Pace: " (show-pace value) " min/km")]
    [:input {:type "range" :value value :min min-value :max max-value
             :style {:width "100%"}
             :on-change (fn [e]
                          (let [new-value (js/parseInt (.. e -target -value))]
                            (reset! pace-data new-value)))}]])

(defn finish-times [pace]
  [:table
   [:thead
     [:tr
      [:th "Distance"]
      [:th "Finish"]]]
   [:tbody
    (for [event events]
      ^{:key event}
      [:tr
        [:td (str (:distance/km event) "km")]
        [:td (show-time (* (:distance/km event) pace))]])]])

(defn main []
  [:div
    [pace-slider @pace-data 180 360]
    [finish-times @pace-data]])

(defn get-app-element []
  (gdom/getElement "app"))

(defn mount [el]
  (rdom/render [main] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

(defn ^:after-load on-reload []
  (mount-app-element))
