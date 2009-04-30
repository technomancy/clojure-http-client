(ns clojure.http.client
  (:use [clojure.contrib.java-utils :only [as-str]]
        [clojure.contrib.duck-streams :only [read-lines]]
        [clojure.contrib.str-utils :only [str-join]])
  (:import (java.net URL HttpURLConnection)))

(defn create-url
  "If url is an instance of java.net.URL then returns it without
modification, otherwise tries to instantiate a java.net.URL with
url as its sole argument."
  [url]
  (if (instance? URL url)
    url
    (URL. url)))

(defn body-seq
  "Returns a lazy-seq of lines from either (.getErrorStream url)
or (.getInputStream url), whichever is appropriate."
  [url]
  (try
   (let [stream (if (>= (.getResponseCode url) 400)
                  (.getErrorStream url)
                  (.getInputStream url))]
     (read-lines stream))))

(defn parse-headers
  "Returns a map of the response headers from url."
  [url]
  (let [hs (.getHeaderFields url)]
    (apply merge (map (fn [e] (when-let [k (key e)]
                                {k (first (val e))}))
                      hs))))

(defn parse-cookies
  "Returns a map of cookies when given the Set-Cookie string sent
by a server."
  [cookie-string]
  (when cookie-string
    (apply merge
           (map (fn [cookie]
                  (apply hash-map
                         (map (fn [c]
                                (.trim c))
                              (.split cookie "="))))
                (.split cookie-string ";")))))

(defn create-cookie-string
  ""
  [cookie-map]
  (str-join "; " (map (fn [cookie]
                        (str (as-str (key cookie))
                             "="
                             (as-str (val cookie))))
                      cookie-map)))

(defn request
  "Perform an HTTP request on url."
  [url & [method cookies]]
  (let [url (.openConnection (create-url url))
        method (.toUpperCase (as-str (or method
                                         "GET")))]
    (.setRequestMethod url method)
    (.setRequestProperty url
                         "User-Agent"
                         "Clojure/pre-1.0 (+http://clojure.org)")
    (.setRequestProperty url
                         "Connection"
                         "close")
    (.setRequestProperty url
                         "Accept"
                         "")
    (when cookies
      (.setRequestProperty url
                           "Cookie"
                           (create-cookie-string cookies)))
    (.connect url)
    (let [headers (parse-headers url)]
      {:body-seq (body-seq url)
       :code (.getResponseCode url)
       :msg (.getResponseMessage url)
       :headers (dissoc headers "Set-Cookie")
       :cookies (parse-cookies (get headers "Set-Cookie" nil))
       :url (str (.getURL url))})))

;; (use '(clojure.contrib pprint))

;; (pprint
;;  (request "http://localhost:2000/" :get
;;           {"Path" "/", "sessionid" "689ac1e1b6e54c8d0f3eb8b063fc0e8f"}))
