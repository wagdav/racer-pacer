(ns racer-pacer.scrubber-scenes
  (:require [portfolio.replicant :refer-macros [defscene]]
            [racer-pacer.core :refer [scrubber-widget]]))

(defscene at-origin
  :title "At origin — no delta yet"
  (scrubber-widget 0.0 "0s"))

(defscene dragging-left
  :title "Dragging left — faster pace"
  (scrubber-widget -0.6 "-12s"))

(defscene dragging-right
  :title "Dragging right — slower pace"
  (scrubber-widget 0.6 "+12s"))
