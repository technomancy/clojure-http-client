# Clojure HTTP Client

by Dan Larkin and Phil Hagelberg

A work in progress.

There are two namespaces, clojure.http.client, which provides a simple
"request" function, and clojure.http.resourcefully, which is targeted
more towards interactions with REST-based APIs.

    (ns clojure.http.example
      (:use [clojure.http.client]
            [clojure.contrib.json.write])
      (:require [clojure.http.resourcefully :as resourcefully]))

    (let [response (request "http://google.com")]
      (:code     response)  ;; 200
      (:msg      response)  ;; "OK"
      (:body-seq response)) ;; ("<html><head><meta[...]" ...

    (resourcefully/put "http://localhost:5984/my-db/doc1" 
                        {} (json-str {:hello "world"}))

    (resourcefully/with-cookies
      (resourcefully/post "http://localhost:3000/login" 
                          {} {"user" user "password" password})
      (resourcefully/get "http://localhost:3000/my-secret-page))

The functions in resourcefully are named after the HTTP verbs. Note
that resourcefully must be required :as something since it defines a
"get" function, which would interfere with core if it were fully
referred. Exceptions will be raised for status codes that indicate
problems, so you don't have to check return codes manually. If you use
resourcefully inside a "with-cookies" block, cookies will
automatically be saved in a *cookies* ref and sent out with each
request.

Licensed under the same terms as Clojure.
