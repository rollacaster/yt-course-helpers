(ns yt-course-helpers.fs
  (:require ["fs" :as fs]
            ["mkdirp" :as mkdirp]))

(defn read-file [path]
  (.toString (.readFileSync fs path)))

(defn write-file [path data]
  (.writeFileSync fs path data))

(defn create-dir [directory]
  (mkdirp directory))
