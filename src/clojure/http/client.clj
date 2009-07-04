(ns clojure.http.client
  (:use [clojure.contrib.java-utils :only [as-str]]
        [clojure.contrib.duck-streams :only [read-lines spit]]
        [clojure.contrib.str-utils :only [str-join]])
  (:import (java.net URL
                     URLEncoder
                     HttpURLConnection)
           (java.io StringReader InputStream)))

(def default-headers {"User-Agent" (str "Clojure/" *clojure-version*
                                        " (+http://clojure.org)"),
                      "Connection" "close"})

(defn set-system-proxy!
  "Java's HttpURLConnection cannot do per-request proxying. Instead,
  system properties are used. This function mutates the global setting.
  For per-request proxying, use the Apache HTTP client."
  [#^String host port]
  (doto (System/getProperties)
    (.setProperty "http.proxyHost" host)
    (.setProperty "http.proxyPort" (str port)))
  nil)
  
(defn url-encode
  "Wrapper around java.net.URLEncoder returning a (UTF-8) URL encoded
representation of argument, either a string or map."
  [arg]
  (if (map? arg)
    (str-join \& (map #(str-join \= (map url-encode %)) arg))
    (URLEncoder/encode (as-str arg) "UTF-8")))

(defn- send-body
  [body #^HttpURLConnection connection headers]
  (.setDoOutput connection true)
  ;; This isn't perfect, since it doesn't account for
  ;; different capitalization etc.
  (when (and (map? body)
             (not (contains? headers "Content-Type")))
    (.setRequestProperty connection
                         "Content-Type"
                         "application/x-www-form-urlencoded"))

  (.connect connection)

  (let [out (.getOutputStream connection)]
    (cond
      (string? body) (spit out body)
      (map? body) (spit out (url-encode body))
      (instance? InputStream body)
      (let [bytes (make-array Byte/TYPE 1000)]
        (loop [#^InputStream stream body
               bytes-read (.read stream bytes)]
          (when (pos? bytes-read)
            (.write out bytes 0 bytes-read)
            (recur stream (.read stream bytes))))))
    (.close out)))

(defn #^URL url
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
  [#^HttpURLConnection connection]
  (read-lines (or (if (>= (.getResponseCode connection) 400)
                    (.getErrorStream connection)
                    (.getInputStream connection))
                  (StringReader. ""))))

(defn- parse-headers
  "Returns a map of the response headers from connection."
  [#^HttpURLConnection connection]
  (let [hs (.getHeaderFields connection)]
    (into {} (for [[k v] hs :when k] [k (first v)]))))

(defn- parse-cookies
  "Returns a map of cookies when given the Set-Cookie string sent
by a server."
  [#^String cookie-string]
  (when cookie-string
    (into {}
      (for [#^String cookie (.split cookie-string ";")]
        (let [keyval (map (fn [#^String x] (.trim x)) (.split cookie "="))]
          [(first keyval) (second keyval)])))))

(defn- create-cookie-string
  "Returns a string suitable for sending to the server in the
\"Cookie\" header when given a clojure map of cookies."
  [cookie-map]
  (str-join "; " (map (fn [cookie]
                        (str #^String (as-str (key cookie))
                             "="
                             #^String (as-str (val cookie))))
                      cookie-map)))

(defn request
  "Perform an HTTP request on URL u."
  [u & [method headers cookies body]]
  
  ;; This function *should* throw an exception on non-HTTP URLs.
  ;; This will happen if the cast fails.
  (let [#^HttpURLConnection connection
        (cast HttpURLConnection (.openConnection (url u)))
        
        method (.toUpperCase #^String (as-str (or method
                                                  "GET")))]
    (.setRequestMethod connection method)

    (doseq [[header value] (conj default-headers (or headers {}))]
      ;; Treat Cookie specially -- see below.
      (when (not (= header "Cookie"))
        (.setRequestProperty connection header value)))

    (when (and cookies (not (empty? cookies)))
      (.setRequestProperty connection
                           "Cookie"
                           (create-cookie-string cookies)))
    
    (if body
      (send-body body connection headers)
      (.connect connection))

    (let [headers (parse-headers connection)]
      {:body-seq (body-seq connection)
       :code (.getResponseCode connection)
       :msg (.getResponseMessage connection)
       :method method
       :headers (dissoc headers "Set-Cookie")
       ;; This correctly implements case-insensitive lookup.
       :get-header #(.getHeaderField connection #^String (as-str %))
       :cookies (parse-cookies (headers "Set-Cookie"))
       :url (str (.getURL connection))})))
