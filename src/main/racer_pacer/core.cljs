(ns racer-pacer.core
  (:require [cljs.core.async :refer [go-loop >! <! chan put! take! close! timeout]]
            [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.spec.alpha :as s]))

(s/def :pace/min-per-km (s/and string? #(re-matches #"[1-5]?[0-9]:[0-5][0-9]" %)))

(def splits
  [{:km 1}
   {:km 5}
   {:km 10
    :url "https://en.wikipedia.org/wiki/10K_run"}
   {:km 15}
   {:km 20}
   {:km 21.0975
    :name "Half marathon"
    :url "https://en.wikipedia.org/wiki/Half_marathon"}
   {:km 30}
   {:km 35}
   {:km 40}
   {:km 42.195
    :name "Marathon"
    :url "https://en.wikipedia.org/wiki/Marathon"}])

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

; 1. Event stream processing
; 2. Event stream coordination
; 3. Interface representation)

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

; Process protocol
;   [:start-drag [x y]]
;   [:drag  [x y]]
;   [:stop-drag]
;   [:set value]

(def mouse-events
   (map (fn [e]
          ({"mousedown"  [:start-drag (.-clientX e)]
            "mousemove"  [:drag       (.-clientX e)]
            "mouseup"    [:stop-drag]}
           (.-type e)))))

(def touch-events
  (map (fn [e]
         ({"touchstart"  [:start-drag (.-clientX (first (.-changedTouches e)))]
           "touchmove"   [:drag (.-clientX (first (.-changedTouches e)))]
           "touchend"    [:stop-drag]
           "touchcancel" [:stop-drag]}
          (.-type e)))))

(defprotocol IAdjustable
  (-get-value [element])
  (-set-value [element value]))

(extend-type reagent.ratom/RAtom
  IAdjustable
  (-get-value [element]
    (deref element))

  (-set-value [element value]
    (reset! element value)))

(defn adjust-proc [events element]
  (let [out (chan)]
    (go-loop [start-pos nil
              start-value nil]
      (let [[event arg] (<! events)]
        (case event
          :start-drag
          (do
            (>! out [event arg])
            (recur arg (-get-value element)))

          :drag
          (do
            (when start-pos
              (let [dx (- arg start-pos)
                    new-value (adjust start-value dx 1)]
                (-set-value element new-value)))
            (recur start-pos start-value))

          :stop-drag
          (do
            (>! out [event arg])
            (recur nil start-value)))))

    out))

(defn adjustable-split [data distance-km]
  (let [d (r/atom {:minutes 1 :seconds 23})
        events (chan 1 (comp
                         mouse-events
                         (filter some?)))
        handler (fn [e]
                  (.preventDefault e)
                  (put! events e))

        out (adjust-proc events d)]

    (go-loop []
      (let [[event arg] (<! out)]
        (case event
          :start-drag
          (doto js/document
            (.addEventListener "mousemove" handler)
            (.addEventListener "mouseup" handler))

          :stop-drag
          (doto js/document
            (.removeEventListener "mousemove" handler)
            (.removeEventListener "mouseup" handler)))

        (recur)))

    (fn [data distance-km]
      [:span
        {:on-mouse-down  handler
         :on-mouse-leave handler}
        (show-time (* distance-km (pace->seconds @d)))])))

; UI components
(defn pace-input [input]
  (let [valid? (:pace @input)]
    [:div.field
      [:label.label {:for "pace"} "Pace"]
      [(if valid? :input.input :input.input.is-danger)
       {:id "pace"
        :type "text"
        :tabIndex 0
        :value (:raw @input)
        :placeholder (show-pace initial-pace)
        :on-change
        (fn [event]
          (let [raw (.. event -target -value)
                pace (parse-pace raw)]
            (reset! input (cond-> {:raw raw}
                                  pace (assoc :pace pace)))))}]
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
      ^{:key (:km split)}
      [:tr
        (if-let [url (split :url)]
          [:td>a {:href url} (or (split :name) (split :km))]
          [:td (split :km)])
        [:td>abbr {:title "Drag to adjust"}
          [adjustable-split pace (split :km)]]])]])

(defonce pace-data (r/atom {:pace initial-pace
                            :raw (show-pace initial-pace)}))

(defn main []
  [:<>
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
  :cljs/quit

  ; Test code
  (def test-events (chan 1))
  (def test-c (adjust-proc test-events nil))

  (put! test-events [:start-drag 0])
  (put! test-events [:drag 10])
  (take! test-c prn))
