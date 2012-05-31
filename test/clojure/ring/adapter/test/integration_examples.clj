(ns ring.adapter.test.integration_examples
  (:use expectations
        ring.util.response)
  (:require [ring.adapter.netty :as netty]
              [clj-http.client :as client]
              [cheshire.core :as json]))

(def sample-header #(response (get-in % [:headers "sample"])))

(defn request [options handler]
  (with-open [server (netty/run-netty handler {:port 8081})]
    (:body (client/get "http://localhost:8081/" options))))

(expect (request {:headers {"sample" "fo"}} sample-header) "fo")


