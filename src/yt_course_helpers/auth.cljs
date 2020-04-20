(ns yt-course-helpers.auth
  (:require ["googleapis" :as googleapis]
            ["opn" :as opn]
            [cljs.reader :as reader]
            [yt-course-helpers.fs :as fs]
            ["http" :as http]
            ["url" :as url]
            ["mkdirp" :as mkdirp]))

(defonce server (atom nil))
(def creds-file (reader/read-string (fs/read-file ".creds.edn")))
(def oauth2-client (new (.-OAuth2 (.-auth (.-google googleapis)))
                        (:client-id creds-file)
                        (:client-secret creds-file)
                        "http://localhost:8080"))
(def token-dir (str (or (.-HOME (.-env js/process)) (.-HOMEPATH (.-env js/process)) (.-USERPROFILE (.-env js/process)))
                    "/.credentials/"))
(def token-path (str token-dir "yt-course-helpser.json"))

(defn get-token [code]
  (.getToken oauth2-client
             code
             (fn [err my-token]
               (if err
                 (println (str "Error while tyring to retrieve access token" err))
                 (do
                   (set! (.-credentials oauth2-client) my-token)
                   (mkdirp token-dir)
                   (fs/write-file token-path (.stringify js/JSON my-token)))))))

(defn handler [req res]
  (let [host (.-host (.-headers req))
        path (.-url req)
        url (new (.-URL url) path (str "http://" host))
        code (.get (.-searchParams url) "code")]
    (if code
      (do
        (get-token code)
        (.writeHead res 200)
        (.end res "Token successfully created.")
        (.close @server))
      (do
        (.writeHead res 404)
        (.end res)))))

(defn authorize []
  (try
    (if (keys (js->clj (.-credentials oauth2-client)))
      oauth2-client
      (let [credentials (.parse js/JSON (fs/read-file token-path))]
        (set! (.-credentials oauth2-client) credentials)
        oauth2-client))
    (catch js/Object _
      (reset! server (.createServer http handler))
      (.listen @server 8080)
      (opn (.generateAuthUrl oauth2-client #js {:scope ["https://www.googleapis.com/auth/youtube"]})))))
