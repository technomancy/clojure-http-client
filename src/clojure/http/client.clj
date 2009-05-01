(ns clojure.http.client
  (:use [clojure.contrib.java-utils :only [as-str]]
        [clojure.contrib.duck-streams :only [read-lines]]
        [clojure.contrib.str-utils :only [str-join]])
  (:import (java.net URL HttpURLConnection)))

(def default-headers {"User-Agent" (str "Clojure/" (clojure-version)
                                        " (+http://clojure.org)"),
                      "Connection" "close"})

(defn url
  "If u is an instance of java.net.URL then returns it without
modification, otherwise tries to instantiate a java.net.URL with
url as its sole argument."
  [u]
  (if (instance? URL u)
    u
    (URL. u)))

(defn- body-seq
  "Returns a lazy-seq of lines from either the input stream
or the error stream of connection, whichever is appropriate."
  [connection]
  (read-lines (if (>= (.getResponseCode connection) 400)
                (.getErrorStream connection)
                (.getInputStream connection))))

(defn- parse-headers
  "Returns a map of the response headers from connection."
  [connection]
  (let [hs (.getHeaderFields connection)]
    (apply merge (map (fn [e] (when-let [k (key e)]
                                {k (first (val e))}))
                      hs))))

(defn- parse-cookies
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

(defn- create-cookie-string
  "Returns a string suitable for sending to the server in the
\"Cookie\" header when given a clojure map of cookies."
[cookie-map]
  (str-join "; " (map (fn [cookie]
                        (str (as-str (key cookie))
                             "="
                             (as-str (val cookie))))
                      cookie-map)))

(defn request
  "Perform an HTTP request on url u."
  [u & [method headers cookies]]
  (let [connection (.openConnection (url u))
        method (.toUpperCase (as-str (or method
                                         "GET")))]
    (.setRequestMethod connection method)

    (doseq [header (conj default-headers (or headers {}))]
      (.setRequestProperty connection
                           (first header)
                           (second header)))

    (when cookies
      (.setRequestProperty connection
                           "Cookie"
                           (create-cookie-string cookies)))
    (.connect connection)
    (let [headers (parse-headers connection)]
      {:body-seq (body-seq connection)
       :code (.getResponseCode connection)
       :msg (.getResponseMessage connection)
       :headers (dissoc headers "Set-Cookie")
       ;; This correctly implements case-insensitive lookup.
       :get-header #(.getHeaderField connection (as-str %))
       :cookies (parse-cookies (get headers "Set-Cookie" nil))
       :url (str (.getURL connection))})))
