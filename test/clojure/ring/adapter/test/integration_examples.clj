(ns ring.adapter.test.integration_examples
  (:use expectations
        ring.util.response)
  (:require [ring.adapter.netty :as netty]
              [clj-http.client :as client]
              [cheshire.core :as json]
              [clojure.java.io :as io]))

;; zerocopy
;;

(declare request response-handler file-handler header-handler stream-handler seq-handler body-handler)

(expect (request {:headers {"Sample" "fo"}} header-handler) "fo")
(expect (request {} file-handler) "Help")
(expect (request {} stream-handler) "Help")
(expect (request {} seq-handler) "Help")
(expect (request {:body "Help"} body-handler) "Help")

(expect {:uri "/part"
         :query-string "key=value"
         :server-port 8081
         :server-name "localhost"
         :scheme "http"
         :keep-alive false
         :request-method "post"
         :remote-addr "127.0.0.1"
         :content-type "application/json"
         :content-length 0} (in (request {:content-type :json :as :json} response-handler)))

(defn request [options handler]
  (with-open [server (netty/run-netty handler {:port 8081})]
    (:body (client/post "http://localhost:8081/part?key=value" options))))

(defn response-handler [req] (response (json/generate-string (dissoc req :request :context :body))))
(defn file-handler [req] (response (io/file "test/clojure/ring/adapter/test/sample.txt")))
(defn stream-handler [req] (response (io/input-stream "test/clojure/ring/adapter/test/sample.txt")))
(defn seq-handler [req] (response '("H" "e" "l" "p")))
(defn header-handler [req] (response (get-in req [:headers "sample"])))
(defn body-handler [req] (response (slurp (:body req))))