(ns yt-course-helpers.core
  (:require ["googleapis" :as googleapis]
            [clojure.string :as s]
            [goog.object :as o]
            [cljs.core.async :refer [go go-loop <!]]
            [cljs.core.async.interop :refer [<p!]]
            [yt-course-helpers.fs :as fs]
            [yt-course-helpers.auth :refer [authorize]]))

(defn list-yt [entity params]
  (go (js->clj (.-data (<p! (.list
                             (o/get (.youtube (.-google googleapis) "v3") entity)
                             (clj->js (assoc params :auth (authorize))))))
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

(defn write-videos-file [videos]
  (fs/write-file "resources/videos.org"
              (s/join
               "\n"
               (map
                (fn [{{:keys [title description]} :snippet}]
                  (str "* " title "\n" description))
                (:items videos)))))

(defn write-my-playlist-to-org-file []
  (go
    (try
      (let [my-playlists (<! (list-yt "playlists" {:part "contentDetails" :mine true}))
            playlist-id (first (map :id (:items my-playlists)))
            video-ids (<! (load-video-ids-of-playlist playlist-id))
            videos (<! (list-yt "videos" {:part "snippet" :id (s/join "," video-ids)}))]
        (write-videos-file videos))
      (catch js/Error err (prn (ex-cause err))))))






