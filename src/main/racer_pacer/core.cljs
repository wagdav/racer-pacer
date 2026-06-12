(ns racer-pacer.core
  (:require [cljs.core.async :as async]
            [goog.dom :as gdom]
            [replicant.dom :as r]
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

; The adjustable protocol works on the :pace key of the store atom.
; After each drag update the :input string is synced to the new pace.
(defrecord PaceAccessor [store]
  IAdjustable
  (-get-value [_]
    (:pace @store))
  (-set-value [_ new-pace]
    (swap! store assoc :pace new-pace :input (show-pace new-pace))))

(defn start-adjustment [element start-event]
  (let [events (async/chan 1 (comp (map mouse-events)
                                   (map touch-events)
                                   (filter :op)))
        handler (fn [e]
                  (.preventDefault e)
                  (async/put! events e))

        event-types ["mousemove" "mouseup" "touchmove" "touchend" "touchcancel"]]

    (.preventDefault start-event)
    (handler start-event)

    (async/go
      (doseq [event-type event-types]
        (.addEventListener js/document event-type handler))
      (async/<! (adjust-proc element events))
      (doseq [event-type event-types]
        (.removeEventListener js/document event-type handler)))))

; UI components
(defn adjustable-split [state-atom distance-km]
  (let [accessor (->PaceAccessor state-atom)]
    [:span
      {:on {:mousedown   (partial start-adjustment accessor)
            :touchstart  (partial start-adjustment accessor)}}
      (show-time (* distance-km (pace->seconds (:pace @state-atom))))]))
(defn pace-input [state-atom]
  (let [{:keys [pace input]} @state-atom
        valid? (parse-pace input)]
    [:div.field
      [:label.label {:for "pace"} "Pace"]
      [(if valid? :input.input :input.input.is-danger)
       {:id "pace"
        :type "text"
        :tabIndex 0
        :value input
        :placeholder (show-pace initial-pace)
        :on {:change
             (fn [event]
               (let [new-input (.. event -target -value)]
                 (if-let [new-pace (parse-pace new-input)]
                   (swap! state-atom assoc :pace new-pace :input new-input)
                   (swap! state-atom assoc :input new-input))))}}]
      (if valid?
        [:p.help "Reference pace (min/km)"]
        [:p.help.is-danger "Should be minutes:seconds. For example 4:45."])]))

(defn split-times [state-atom]
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
          [:td [:a {:href url} (or (split :name) (split :km))]]
          [:td (split :km)])
        [:td [:abbr {:title "Drag to adjust"}
              (adjustable-split state-atom (split :km))]]])]])

(defonce store
  (atom {:pace  initial-pace
         :input (show-pace initial-pace)}))

(def github-url "https://github.com/wagdav/racer-pacer")

(defn main-view []
  (list
    [:section.section
      [:h1.title "Splits calculator"]
      [:div.columns
        [:div.column
          (pace-input store)]
        [:div.column
          (split-times store)]]]
    [:footer.footer
      [:div.content.has-text-centered
        [:p
          "This is an experiment written in "
          [:a {:href "https://clojurescript.org"} "ClojureScript"] ". "
          "The source code is available on " [:a {:href github-url} "GitHub"] "."]
        [:p.has-text-weight-light.is-size-7
          "Revision: "
          [:a {:href (str github-url "/commit/" revision)} (subs revision 0 (min 6 (count revision)))]]]]))

(defonce dom-el (gdom/getElement "app"))

(defn render! []
  (r/render dom-el (main-view)))

(defn ^:dev/after-load start []
  (render!))

(defn init []
  (add-watch store ::render (fn [_ _ _ _] (render!)))
  (start))

(comment
  ; Evaluate these lines to enter into a ClojureScript REPL
  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :app)
  ; Exit the CLJS session
  :cljs/quit)
