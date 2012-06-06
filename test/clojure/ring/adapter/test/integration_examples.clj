(ns ring.adapter.test.integration_examples
  (:use expectations
        ring.util.response)
  (:require [ring.adapter.netty :as netty]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(declare request-to response-handler file-handler header-handler stream-handler seq-handler body-handler server)

(expect "fo" (request-to (server header-handler) {:headers {"Sample" "fo"}}))
(expect "Help" (request-to (server file-handler)))
(expect "Help" (request-to (server stream-handler)))
(expect "Helper" (request-to (server seq-handler)))
(expect "Help Me" (request-to (server body-handler) {:body "Help Me"}))
(expect "Help" (request-to (server file-handler {:zerocopy true})))

(expect {:uri "/part"
         :query-string "key=value"
         :server-port 8081
         :server-name "localhost"
         :scheme "http"
         :keep-alive false
         :request-method "post"
         :remote-addr "127.0.0.1"
         :content-type "application/json"
         :content-length 0} (in (request-to (server response-handler) {:content-type :json :as :json})))

(defn request-to
  ([netty] (request-to netty {}))
  ([netty request-options]
    (with-open [server netty]
      (:body (client/post "http://localhost:8081/part?key=value" request-options)))))

(defn response-handler [req] (response (json/generate-string (dissoc req :request :context :body ))))
(defn file-handler [req] (response (io/file "test/clojure/ring/adapter/test/sample.txt")))
(defn stream-handler [req] (response (io/input-stream "test/clojure/ring/adapter/test/sample.txt")))
(defn seq-handler [req] (response '("H" "e" "l" "p" "e" "r")))
(defn header-handler [req] (response (get-in req [:headers "sample"])))
(defn body-handler [req] (response (slurp (:body req))))

(defn server
  ([handler] (server handler {}))
  ([handler options] (netty/run-netty handler (merge {:port 8081} options))))