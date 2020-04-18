(ns yt-course-helpers.auth
  (:require ["googleapis" :as googleapis]
            ["opn" :as opn]
            [cljs.reader :as reader]
            [yt-course-helpers.fs :as fs]))

(def o-auth2 (.-OAuth2 (.-auth (.-google googleapis))))
(def scopes  #js ["https://www.googleapis.com/auth/youtube"])
(def creds-file (reader/read-string (fs/read-file ".creds.edn")))
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
