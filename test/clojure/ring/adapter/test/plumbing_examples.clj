(ns ring.adapter.test.plumbing_examples
  (:use expectations)
  (:use ring.adapter.plumbing)
  (:import (org.jboss.netty.handler.codec.http DefaultHttpRequest HttpVersion HttpMethod HttpHeaders HttpHeaders$Names)))

(declare request)

;; method-keyword
(expect :connect (method-keyword HttpMethod/CONNECT))
(expect :delete (method-keyword HttpMethod/DELETE))
(expect :get (method-keyword HttpMethod/GET))
(expect :head (method-keyword HttpMethod/HEAD))
(expect :options (method-keyword HttpMethod/OPTIONS))
(expect :patch (method-keyword HttpMethod/PATCH))
(expect :post (method-keyword HttpMethod/POST))
(expect :put (method-keyword HttpMethod/PUT))
(expect :trace (method-keyword HttpMethod/TRACE))

;; add method
(expect :get (method (request {:method HttpMethod/GET})))
(expect :post (method (request {:method HttpMethod/POST})))

;; add keep alive
(expect true (keep-alive? (request {:keep-alive true})))
(expect false (keep-alive? (request {:keep-alive false})))

;; add uri and query-string
(expect ["/" nil] (url (request {:uri "/"})))
(expect ["/apple" "you=me"] (url (request {:uri "/apple?you=me"})))
(expect ["/apple" "you=me&help=me"] (url (request {:uri "/apple?you=me&help=me"})))

;; content-length
(expect 5 (content-length (request {:content-length 5})))
(expect 15 (content-length (request {:content-length 15})))
(expect (nil? (content-length (request {}))) true)

;; scheme
(expect "http" (scheme (request {:scheme "http"})))
(expect "https" (scheme (request {:scheme "https"})))
(expect "http" (scheme (request {})))

;; character encoding
(expect "whocares" (character-encoding (request {:character-encoding "whocares"})))
(expect "behere" (character-encoding (request {:character-encoding "behere"})))
(expect (nil? (character-encoding (request {}))) true)

;; headers
(expect {"x-scheme" "http"} (headers (request {:scheme "http"})))
(expect {"content-length" "5"} (headers (request {:content-length 5})))

;; content-type
(expect "foo" (content-type (request {:content-type "foo"})))

(defn request [req]
  (let [method (get req :method HttpMethod/GET)
        keep-alive (get req :keep-alive true)
        uri (get req :uri "/")
        content-length (get req :content-length)
        scheme (get req :scheme)
        encoding (get req :character-encoding)
        content-type (get req :content-type)
        http-request (DefaultHttpRequest. HttpVersion/HTTP_1_1, method, uri)]
  (do
    (HttpHeaders/setKeepAlive http-request keep-alive)
    (if (not (nil? content-length)) (HttpHeaders/setContentLength http-request content-length))
    (if (not (nil? scheme)) (HttpHeaders/setHeader http-request "x-scheme" scheme))
    (if (not (nil? encoding)) (HttpHeaders/setHeader http-request HttpHeaders$Names/CONTENT_ENCODING encoding))
    (if (not (nil? content-type)) (HttpHeaders/setHeader http-request HttpHeaders$Names/CONTENT_TYPE content-type))
    http-request)))