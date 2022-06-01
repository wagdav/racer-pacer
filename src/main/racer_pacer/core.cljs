(ns ^:figwheel-hooks racer-pacer.core
  (:require [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.spec.alpha :as s]))

(s/def :pace/min-per-km (s/and string? #(re-matches #"[1-5]?[0-9]:[0-5][0-9]" %)))

(def marathon 42.195)
(def half-marathon 21.0975)
(def splits [1 5 10 15 20 half-marathon 30 35 40 marathon])

(def annotations {half-marathon {:name "Half marathon"
                                 :url "https://en.wikipedia.org/wiki/Half_marathon"}
                  5 {:url "https://en.wikipedia.org/wiki/5K_run"}
                  10 {:url "https://en.wikipedia.org/wiki/10K_run"}
                  marathon {:name "Marathon"
                            :url "https://en.wikipedia.org/wiki/Marathon"}})

(defn parse-pace [t]
  (when (s/valid? :pace/min-per-km t)
    (let [[minutes seconds] (clojure.string/split t #":")]
      {:minutes (js/parseInt minutes)
       :seconds (js/parseInt seconds)})))

(defn seconds->pace [secs]
  (let [hours (quot secs 3600)
        minutes (quot (- secs (* hours 3600)) 60)
        seconds (- secs (* hours 3600) (* minutes 60))]
    {:hours hours
     :minutes minutes
     :seconds seconds}))

(defn pace->seconds [{:keys [minutes seconds]}]
  (+ seconds (* 60 minutes)))

(defn show-pace [p]
  (gstring/format "%d:%02d" (:minutes p) (:seconds p)))

(defn show-time [secs]
  (let [p (seconds->pace secs)]
    (gstring/format "%d:%02d:%02d" (:hours p) (:minutes p) (:seconds p))))

(defn pace-input [data]
  [:div
   {:class ["field" "is-horizontal"]}
   [:div.field-label
     [:label.label "Pace"]]
   [:div.field-body
     [:div.field
       [:div.control
         [:input.input
          {:type "text"
           :value @data
           :on-change
           (fn [event]
             (reset! data (.. event -target -value)))}]]
       [:p.help "Reference pace in (min/km)"]]]])

(defn adjust [value dx step]
  (if-let [pace (parse-pace value)]
    (-> pace
        pace->seconds
        (+ (* dx step 0.2))
        (/ step)
        (#(.round js/Math %))
        (* step)
        seconds->pace
        show-pace)
    value))

(defn mouse-move [start step pace]
  (fn [e]
    (let [dx (- (.-clientX e) (@start :x))
          value (@start :value)]
      (reset! pace (adjust value dx step)))))

(defn touch-move [start step pace]
  (fn [e]
    (let [touches (.-changedTouches e)
          dx (- (.-clientX (first touches)) (@start :x))
          value (@start :value)]
      (reset! pace (adjust value dx step)))))

(defn adjustable-split [pace distance-km]
  (let [start (r/atom {})
        step 1]

    (fn [pace distance-km]
      [:span
       {:on-mouse-down
        (fn [e]
          (.preventDefault e)
          (swap! start assoc :value @pace
                             :x (.-clientX e))
          (let [document (.. e -target -ownerDocument)
                handler (mouse-move start step pace)]
            (.addEventListener document "mousemove" handler)
            (.addEventListener document "mouseup" #(.removeEventListener document "mousemove" handler))))

        :on-touch-start
        (fn [e]
          (.preventDefault e)
          (swap! start assoc :value @pace
                             :x (.-clientX (first (.-changedTouches e))))
          (let [document (.. e -target -ownerDocument)
                handler (touch-move start step pace)]
            (.addEventListener document "touchmove" handler)
            (.addEventListener document "touchend" #(.removeEventListener document "touchmove" handler))
            (.addEventListener document "touchcancel" #(.removeEventListener document "touchmove" handler))))}

       (show-time (* distance-km (pace->seconds (parse-pace @pace))))])))

(defn split-times [pace]
  [:table
   {:class ["table" "is-striped"]}
   [:thead
    [:tr
      [:th "Km"]
      [:th "Split"]]]
   [:tbody
    (for [split splits]
      ^{:key split}
      [:tr
        (if-let [url (get-in annotations [split :url])]
          [:td [:a {:href url} (get-in annotations [split :name] split)]]
          [:td (get-in annotations [split :name] split)])
        [:td [adjustable-split pace split]]])]])

(defonce pace-data (r/atom "4:35"))

(defn main []
  [:section
    [:div.container
      [pace-input pace-data]
      [split-times pace-data]]])

(defn mount []
  (rdom/render [main] (gdom/getElement "app")))

(defn ^:dev/after-load on-reload []
  (mount))

(defonce startup (do (mount) true))
