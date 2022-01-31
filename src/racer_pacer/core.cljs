(ns ^:figwheel-hooks racer-pacer.core
  (:require [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.string :as gstring]
            [goog.string.format]))

;(def pace-data (r/atom {:minutes 4 :seconds 5}))
(defonce pace-data (r/atom 275))

(def marathon 42.195)
(def half-marathon 21.0975)

(def splits [1 5 10 15 20 half-marathon 30 35  40 marathon])

(def annotations {half-marathon {:name "Half marathon"
                                 :url "https://en.wikipedia.org/wiki/Half_marathon"}
                  5 {:url "https://en.wikipedia.org/wiki/5K_run"}
                  10 {:url "https://en.wikipedia.org/wiki/10K_run"}
                  marathon {:name "Marathon"
                            :url "https://en.wikipedia.org/wiki/Marathon"}})

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

(defn parse-input [text]
  (let [[_ minutes seconds] (re-find #"^(\d):(\d\d)$" text)]
    (and minutes seconds {:minutes (js/parseInt minutes) :seconds (js/parseInt seconds)})))

(defn pace-input []
  (let [new-pace (r/atom "4:35")]
    (fn []
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                           (when-let [new-value (parse-input @new-pace)]
                             (reset! pace-data (pace->seconds new-value))))}
        [:input {:type "text" :value @new-pace
                 :on-change (fn [e]
                               (reset! new-pace (.. e -target -value)))}]])))


(defn pace-slider [value min-value max-value]
  [:div
    [:p
     "Splits for a reference pace of "
     [pace-input]
     "minutes per kilometer:"]])

(defn split-times [pace]
  [:table
   [:thead
    [:tr
      [:th "Km"]
      [:th "⏰"]]]
   [:tbody
    (for [split splits]
      ^{:key split}
      [:tr
         (if-let [url (get-in annotations [split :url])]
            [:td [:a {:href url} (get-in annotations [split :name] split)]]
            [:td (get-in annotations [split :name] split)])
        [:td (show-time (* split pace))]])]])

(defn main []
  [:div
    [:h1 "Splits"]
    [pace-slider @pace-data 180 360]
    [split-times @pace-data]])

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
