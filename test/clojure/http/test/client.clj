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
     (reduce #(conj %1 (str-join ":" %2))
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
  (let [response (request (str "http://localhost:" test-port))]
    (is (= "OK" (:msg response)))
    (is (= 200 (:code response)))
    (is (= "GET / HTTP/1.1" (first (:body-seq response))))))

(deftest custom-header-get
  (let [response (request (str "http://localhost:" test-port)
                          :get {"How-Awesome" "very"})]
    (is (some #{"How-Awesome: very"} (:body-seq response)))))

(deftest case-insensitive-headers
  (let [response (request (str "http://localhost:" test-port))]
    (is (= "text/plain" ((:get-header response) "content-type")))))

;; need echo response to work with body before this will work.
;; (deftest request-body
;;   (let [response (request (str "http://localhost:" test-port)
;;                           :get {} {} {"hey" "düde"})]
;;     (is (some #{"o=hai+dere&hey=d%C3%B6od"} (:body-seq response)))))