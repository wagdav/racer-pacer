(ns racer-pacer.core
  (:require [cljs.core.async :as async]
            [goog.dom :as gdom]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.spec.alpha :as s]))

(goog-define ^string revision "main")

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

; Process protocol
;   {:op :start-drag :x <x-coordinate>}
;   {:op :drag       :x <x-coordinate>}
;   {:op :stop-drag}}
(defn mouse-events [e]
  (case (.-type e)
   "mousedown"  {:op :start-drag
                 :x (.-clientX e)}
   "mousemove"  {:op :drag
                 :x (.-clientX e)}
   "mouseup"    {:op :stop-drag}
   e))

(defn touch-events [e]
  (case (.-type e)
    "touchstart"  {:op :start-drag
                   :x  (.-clientX (first (.-changedTouches e)))}
    "touchmove"   {:op :drag
                   :x (.-clientX (first (.-changedTouches e)))}
    "touchend"    {:op :stop-drag}
    "touchcancel" {:op :stop-drag}
    e))

(defprotocol IAdjustable
  (-get-value [element])
  (-set-value [element value]))

(defn adjust-proc [element events]
 (async/go-loop [start-pos 0
                 start-value (-get-value element)]
   (let [{op :op :as event} (async/<! events)]
     (case op
       :start-drag
       (recur (:x event) (-get-value element))

       :drag
       (let [dx (- (:x event) start-pos)
             new-value (adjust start-value dx 1)]
         (-set-value element new-value)
         (recur start-pos start-value))

       :stop-drag))))

; Implementation with React/Reagent
(extend-type reagent.ratom/RAtom
  IAdjustable
  (-get-value [element]
    (deref element))

  (-set-value [element value]
    (reset! element value)))

(defn handle-listeners [events handler]
  (async/go-loop []
    (let [{op :op} (async/<! events)
          event-types ["mousemove" "mouseup"
                       "touchmove" "touchend" "touchcancel"]]
      (case op
        :start-drag
        (do
          (doseq [event-type event-types]
            (.addEventListener js/document event-type handler))
          (recur))

        :stop-drag
        (doseq [event-type event-types]
          (.removeEventListener js/document event-type handler))

        (recur)))))

(defn adjustable-split [data distance-km]
  (let [events (async/chan 1 (comp (map mouse-events)
                                   (map touch-events)
                                   (filter :op)))
        handler (fn [e]
                  (.preventDefault e)
                  (async/put! events e))

        start-procs (fn [e]
                      (handler e)
                      (let [m (async/mult events)]
                        (adjust-proc data (async/tap m (async/chan)))
                        (handle-listeners (async/tap m (async/chan)) handler)))]

    (fn [data distance-km]
      [:span
        {:on-mouse-down start-procs
         :on-touch-start start-procs}

        (show-time (* distance-km (pace->seconds @data)))])))

; UI components
(defn pace-input [reference-pace]
  (let [input-value (r/atom (show-pace @reference-pace))]
    (add-watch reference-pace
               :changed
               #(reset! input-value (show-pace %4)))
    (fn [_]
      (let [valid? (parse-pace @input-value)]
        [:div.field
          [:label.label {:for "pace"} "Pace"]
          [(if valid? :input.input :input.input.is-danger)
           {:id "pace"
            :type "text"
            :tabIndex 0
            :value @input-value
            :placeholder (show-pace initial-pace)
            :on-change
            (fn [event]
              (let [new-value (.. event -target -value)]
                (reset! input-value new-value)
                (when-let [new-pace (parse-pace new-value)]
                  (reset! reference-pace new-pace))))}]

          (if valid?
            [:p.help "Reference pace (min/km)"]
            [:p.help.is-danger "Should be minutes:seconds. For example 4:45."])]))))

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

(defonce pace-data (r/atom initial-pace))

(def github-url "https://github.com/wagdav/racer-pacer")

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
          "The source code is available on " [:a {:href github-url} "GitHub"] "."]
        [:p.has-text-weight-light.is-size-7
          "Revision: "
          [:a {:href (str github-url "/commit/" revision)} (take 6 revision)]]]]])

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
