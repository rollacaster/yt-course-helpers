(ns yt-course-helpers.core
  (:require ["node-fetch" :as fetch]
            ["fs" :as fs]
            ["googleapis" :as googleapis]
            ["opn" :as opn]
            [clojure.string :as s]
            [cljs.core.async :refer [go go-loop <!]]
            [cljs.core.async.interop :refer [<p!]]
            [cljs.reader :as reader]))

(defn read-file [path]
  (.toString (.readFileSync fs path)))

(defn write-file [path data]
  (.writeFileSync fs path data))

(def o-auth2 (.-OAuth2 (.-auth (.-google googleapis))))
(def scopes  #js ["https://www.googleapis.com/auth/youtube"])
(def creds-file (reader/read-string (read-file ".creds.edn")))
(def client-secret (:client-secret creds-file))
(def client-id (:client-id creds-file))
(def redirect-uri "http://localhost:8080")
(def oauth2-client (new o-auth2 client-id client-secret redirect-uri))
(def url (.generateAuthUrl oauth2-client #js {:scope scopes}))
(defn authenticate [] (opn url))

(defonce token (atom nil))

(defn get-token [code]
  (.getToken oauth2-client
             code
             (fn [_ my-token] (reset! token (.-access_token my-token)))))

(defn query-params [params]
  (s/join "&" (map (fn [[key value]] (str (name key) "=" value)) params)))

(defn GET
  ([endpoint]
   (-> (fetch (str "https://www.googleapis.com/youtube/v3/" endpoint)
              (clj->js {:headers {:Content-Type "Application/json"
                                  :Authorization (str "Bearer " @token)}}))
       (.then #(.json %))
       (.then #(js->clj % :keywordize-keys true))))
  ([endpoint params]
   (GET (str endpoint "?" (query-params params)))))

(defn load-my-playlists []
  (GET "playlists" {:part "contentDetails" :mine true}))

(defn load-playlist-items [params]
  (GET "playlistItems" params))

(defn load-video-ids-of-playlist [playlist-id]
  (go-loop [video-ids [] pageToken nil]
    (let [{:keys [items nextPageToken]}
          (<p! (load-playlist-items {:part "snippet,contentDetails"
                                     :playlistId playlist-id
                                     :pageToken pageToken}))]
      (if nextPageToken
        (recur (concat video-ids (map (comp :videoId :contentDetails) items))
               nextPageToken)
        (concat video-ids (map (comp :videoId :contentDetails) items))))))

(defn convert-videos [videos]
  (map (fn [video]
         {:title (get-in video [:snippet :title])
          :description (get-in video [:snippet :description])})
       (:items videos)))

(defn write-videos-file [videos]
  (write-file "resources/videos.org"
              (s/join
               "\n"
               (map
                (fn [{:keys [title description]}]
                  (str "* " title "\n" description))
                videos))))

(defn write-my-playlist-to-org-file []
  (go
    (try
      (let [my-playlists (<p! (load-my-playlists))
            playlist-id (first (map :id (:items my-playlists)))
            video-ids (<! (load-video-ids-of-playlist playlist-id))
            videos (<p! (GET (str "videos?part=snippet&id="
                                  (s/join "," video-ids))))]
        (write-videos-file (convert-videos videos)))
      (catch js/Error err (prn (ex-cause err))))))




