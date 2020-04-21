(ns yt-course-helpers.core
  (:require ["googleapis" :as googleapis]
            [cljs.core.async :refer [<! go go-loop]]
            [cljs.core.async.interop :refer [<p!]]
            [clojure.string :as s]
            [goog.object :as o]
            [yt-course-helpers.auth :refer [authorize]]
            [yt-course-helpers.fs :as fs]
            [instaparse.core :as insta]))

(defn list-yt [entity params]
  (go (js->clj (.-data (<p! (.list
                             (o/get (.youtube (.-google googleapis) "v3") entity)
                             (clj->js (assoc params :auth (authorize))))))
               :keywordize-keys true)))

(defn update-yt [entity body]
  (go (js->clj (.-data (<p! (.update
                             (o/get (.youtube (.-google googleapis) "v3") entity)
                             (clj->js {:part "snippet"
                                       :auth (authorize)
                                       :resource (clj->js body)}))))
               :keywordize-keys true)))


(defn load-video-ids-of-playlist [playlist-id]
  (go-loop [video-ids [] pageToken ""]
    (let [{:keys [items nextPageToken]} (<! (list-yt "playlistItems"
                                                     {:part "snippet,contentDetails"
                                                      :playlistId playlist-id
                                                      :pageToken pageToken}))
          all-ids (concat video-ids (map (comp :videoId :contentDetails) items))]
      (if nextPageToken
        (recur all-ids nextPageToken)
        all-ids))))

(defn load-my-videos []
  (go
    (let [my-playlists (<! (list-yt "playlists" {:part "contentDetails" :mine true}))
          playlist-id (first (map :id (:items my-playlists)))
          video-ids (<! (load-video-ids-of-playlist playlist-id))
          videos (<! (list-yt "videos" {:part "snippet" :id (s/join "," video-ids)}))]
      (:items videos))))

(def videos-path "resources/videos.org")

(defn write-videos-file [videos]
  (go
    (fs/write-file videos-path
                   (s/join
                    "\n"
                    (map
                     (fn [{{:keys [title description]} :snippet}]
                       (str "* " title "\n" description))
                     videos)))))


(defn read-videos-file []
  (map
   (fn [[[[_ _ [_ & title]]] & description]]
     {:title (s/join " " title)
      :description (s/join "\n" (filter string? (map (fn [line] (if (= line :empty-line) "" line))(flatten description))))})
   (partition 2
              (partition-by
               #(do
                  (= :head-line (first %)))
               (drop 1 ((insta/parser (fs/read-file "resources/org.ebnf"))
                        (fs/read-file videos-path)))))))

(defn update-video-descriptions [yt-videos videos]
  (go
    (doseq [yt-video yt-videos]
      (let [updated-videos (some (fn [video] (when (= (:title video) (:title (:snippet yt-video))) video)) videos)]
        (<! (update-yt "videos" (assoc-in yt-video [:snippet :description] (:description updated-videos))))))))

(comment
  ;; Load files from yt and write them to disk
  (go (write-videos-file (<! (load-my-videos))))
  ;; Update yt-videos with file from disk
  (go
    (update-video-descriptions (<! (load-my-videos)) (read-videos-file))))










