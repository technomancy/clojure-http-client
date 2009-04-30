;; (add-classpath "file:///home/phil/src/clojure-http-client/src/")

(ns clojure.http.test.client
  (:use [clojure.http.client] :reload)
  (:use [clojure.contrib.test-is]
        [clojure.contrib.duck-streams]
        [clojure.contrib.str-utils]
        [clojure.contrib.server-socket]))

(def test-port 8239)

(defn response-headers
  ([headers]
     (reduce #(conj %1 (str (first %2) ": " (second %2)))
             '("HTTP/1.1 200 OK"
               "Server: clojure-http-test-client"
               "Content-Type: text/plain")
             headers)))

(defn echo-http-response [ins outs]
  (with-open [in (reader ins), out (writer outs)]
    (binding [*in* in, *out* out]
      (println (str-join "\n" (response-headers {})))
      (println)
      (loop [input (read-line)]
        ;; TODO: this conditional ignores request bodies
        (when (not= "" input)
          (println input)
          (recur (read-line)))))))

;; (.close (:server-socket server))
;; (def server (create-server test-port echo-http-response))

(defonce server (create-server test-port echo-http-response))

(deftest simple-get
  (let [response (request (format "http://localhost:%s/" test-port)
                          :get {"Path" "/"})]
    (is (= "OK" (:msg response)))
    (is (= 200 (:code response)))
    (is (= "GET / HTTP/1.1" (first (:body-seq response))))))