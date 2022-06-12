(ns racer-pacer.core
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

(def initial-pace {:minutes 4 :seconds 35})

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

(defn adjust [value dx step]
  (-> value
      pace->seconds
      (+ (* dx step 0.2))
      (/ step)
      (#(.round js/Math %))
      (* step)
      seconds->pace))

(adjust {:minutes 4 :seconds 13} -100 1)

(defn mouse-move [start step data]
  (fn [e]
    (let [dx (- (.-clientX e) (@start :x))
          value (@start :value)
          new-pace (adjust value dx step)]
      (swap! data assoc :raw (show-pace new-pace) :pace new-pace))))

(defn touch-move [start step data]
  (fn [e]
    (let [touches (.-changedTouches e)
          dx (- (.-clientX (first touches)) (@start :x))
          value (@start :value)
          new-pace (adjust value dx step)]
      (swap! data assoc :raw (show-pace new-pace) :pace new-pace))))

(defn adjustable-split [data distance-km]
  (let [last-valid (r/atom (:pace @data))
        start (r/atom {})
        step 1]

    (fn [data distance-km]
      ; Remember the last valid value
      (when-let [p (:pace @data)]
        (reset! last-valid p))

      [:span
       {:on-mouse-down
        (fn [e]
          (.preventDefault e)
          (swap! start assoc :value (:pace @data)
                             :x (.-clientX e))
          (let [document (.. e -target -ownerDocument)
                handler (mouse-move start step data)]
            (.addEventListener document "mousemove" handler)
            (.addEventListener document "mouseup" #(.removeEventListener document "mousemove" handler))))

        :on-touch-start
        (fn [e]
          (.preventDefault e)
          (swap! start assoc :value (:pace @data)
                             :x (.-clientX (first (.-changedTouches e))))
          (let [document (.. e -target -ownerDocument)
                handler (touch-move start step data)]
            (.addEventListener document "touchmove" handler)
            (.addEventListener document "touchend" #(.removeEventListener document "touchmove" handler))
            (.addEventListener document "touchcancel" #(.removeEventListener document "touchmove" handler))))}

       (show-time (* distance-km (pace->seconds @last-valid)))])))

; UI components
(defn pace-input [input]
  (let [valid? (:pace @input)]
    [:div.field
      [:label.label "Pace"]
      [:div.control
        [(if valid? :input.input :input.input.is-danger)
         {:type "text"
          :value (:raw @input)
          :placeholder (show-pace initial-pace)
          :on-change
          (fn [event]
            (let [raw (.. event -target -value)
                  pace (parse-pace raw)]
              (reset! input (cond-> {:raw raw}
                                    pace (assoc :pace pace)))))}]]
      (if valid?
        [:p.help "Reference pace (min/km)"]
        [:p.help.is-danger "Should be minutes:seconds. For example 4:45."])]))

(defn split-times [pace]
  [:table.table.is-striped.is-fullwidth
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
        [:td
          [:abbr
           {:title "Drag to adjust"}
           [adjustable-split pace split]]]])]])

(defonce pace-data (r/atom {:pace initial-pace
                            :raw (show-pace initial-pace)}))

(defn main []
  [:div
    [:section.section
      [:h1.title "Splits calculator"]
      [:div.columns
        [:div.column
          [pace-input pace-data]]
        [:div.column
          [split-times pace-data]]]]
    [:footer.footer
      [:div.content.has-text-centered
        [:p
          "This is an experiment written in "
          [:a {:href "https://clojurescript.org"} "ClojureScript"] ". "
          "The source code is available on "
          [:a {:href "https://github.com/wagdav/racer-pacer"} "GitHub"] "."]]]])

(defn mount []
  (rdom/render [main] (gdom/getElement "app")))

(defn ^:dev/after-load on-reload []
  (mount))

(defonce startup (do (mount) true))

(comment
  ; Evaluate these lines to enter into a ClojureScript REPL
  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :app)
  ; Exit the CLJS session
  :cljs/quit)
