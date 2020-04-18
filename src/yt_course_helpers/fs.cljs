(ns yt-course-helpers.fs
  (:require ["fs" :as fs]))

(defn read-file [path]
  (.toString (.readFileSync fs path)))

(defn write-file [path data]
  (.writeFileSync fs path data))
