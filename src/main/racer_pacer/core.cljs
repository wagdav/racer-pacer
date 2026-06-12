(ns racer-pacer.core
  (:require [clojure.string :as str]
            [goog.dom :as gdom]
            [replicant.dom :as r]
            [nexus.core :as nexus]
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
    (let [[minutes seconds] (str/split t #":")]
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

(defn adjust [value dx]
  (-> value
      pace->seconds
      (+ (* dx 0.2))
      (#(.round js/Math %))
      seconds->pace))

; Actions
;   [:pace/start-drag x]  — mousedown/touchstart; seeds drag state
;   [:pace/drag       x]  — mousemove/touchmove; computes new pace from delta
;   [:pace/stop-drag]     — mouseup/touchend; clears drag state and listeners
;   [:pace/set-input  s]  — text input change

; Effects
;   [:effects/save [path value] ...]     — batched assoc-in on store
;   [:effects/add-drag-listeners]        — attaches document-level move/up listeners
;   [:effects/remove-drag-listeners]     — detaches them

(declare nexus)

(def event-types ["mousemove" "mouseup" "touchmove" "touchend" "touchcancel"])

(defn- client-x [e]
  (case (.-type e)
    ("mousedown" "mousemove")
    (.-clientX e)
    ("touchstart" "touchmove" "touchend" "touchcancel")
    (.-clientX (first (.-changedTouches e)))))

(def nexus-map
  {:nexus/system->state deref

   :nexus/placeholders
   {:event/client-x
    (fn [{:replicant/keys [dom-event]}]
      (client-x dom-event))

    :event/input-value
    (fn [{:replicant/keys [dom-event]}]
      (.. dom-event -target -value))}

   :nexus/actions
   {:pace/start-drag
    (fn [state x]
      [[:effects/prevent-default]
       [:effects/save [:drag :start-pos]   x]
       [:effects/save [:drag :start-value] (:pace state)]
       [:effects/add-drag-listeners]])

    :pace/drag
    (fn [state x]
      (let [{:keys [start-pos start-value]} (:drag state)
            new-pace (adjust start-value (- x start-pos))]
        [[:effects/save [:pace]  new-pace]
         [:effects/save [:input] (show-pace new-pace)]]))

    :pace/stop-drag
    (fn [_state]
      [[:effects/remove-drag-listeners]
       [:effects/save [:drag] nil]])

    :pace/set-input
    (fn [_state input]
      (if-let [new-pace (parse-pace input)]
        [[:effects/save [:pace]  new-pace]
         [:effects/save [:input] input]]
        [[:effects/save [:input] input]]))}

   :nexus/effects
   {:effects/prevent-default
    (fn [{:keys [dispatch-data]} _store]
      (some-> (:replicant/dom-event dispatch-data) .preventDefault))

    :effects/save
    ^:nexus/batch
    (fn [_ store path-vs]
      (swap! store (fn [s] (reduce (fn [acc [p v]] (assoc-in acc p v)) s path-vs))))

    :effects/add-drag-listeners
    (fn [_ store]
      (let [handler (fn [e]
                      (.preventDefault e)
                      (nexus/dispatch nexus-map store {:replicant/dom-event e}
                        [(case (.-type e)
                           ("mousemove" "touchmove")
                           [:pace/drag [:event/client-x]]
                           ("mouseup" "touchend" "touchcancel")
                           [:pace/stop-drag])]))]
        (swap! store assoc-in [:drag :handler] handler)
        (doseq [t event-types]
          (.addEventListener js/document t handler))))

    :effects/remove-drag-listeners
    (fn [_ store]
      (let [handler (get-in @store [:drag :handler])]
        (doseq [t event-types]
          (.removeEventListener js/document t handler))))}})

; UI components

(defn adjustable-split [state distance-km]
  [:span
   {:on {:mousedown  [[:pace/start-drag [:event/client-x]]]
         :touchstart [[:pace/start-drag [:event/client-x]]]}}
   (show-time (* distance-km (pace->seconds (:pace state))))])

(defn pace-input [state]
  (let [{:keys [input]} state
        valid? (parse-pace input)]
    [:div.field
     [:label.label {:for "pace"} "Pace"]
     [(if valid? :input.input :input.input.is-danger)
      {:id "pace"
       :type "text"
       :tabIndex 0
       :value input
       :placeholder (show-pace initial-pace)
       :on {:change [[:pace/set-input [:event/input-value]]]}}]
     (if valid?
       [:p.help "Reference pace (min/km)"]
       [:p.help.is-danger "Should be minutes:seconds. For example 4:45."])]))

(defn split-times [state]
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
             (adjustable-split state (split :km))]]])]])

(defonce store
  (atom {:pace  initial-pace
         :input (show-pace initial-pace)
         :drag  nil}))

(def github-url "https://github.com/wagdav/racer-pacer")

(defn main-view [state]
  (list
    [:section.section
     [:h1.title "Splits calculator"]
     [:div.columns
      [:div.column
       (pace-input state)]
      [:div.column
       (split-times state)]]]
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

(defn render! [state]
  (r/render dom-el (main-view state)))

(defn ^:dev/after-load start []
  (render! @store))

(defn init []
  (r/set-dispatch! #(nexus/dispatch nexus-map store %1 %2))
  (add-watch store ::render (fn [_ _ _ state] (render! state)))
  (start))

(comment
  ; Evaluate these lines to enter into a ClojureScript REPL
  (require '[shadow.cljs.devtools.api :as shadow])
  (shadow/repl :app)
  ; Exit the CLJS session
  :cljs/quit)
