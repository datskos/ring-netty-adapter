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
(expect {:request-method :get} (in (add-method (request {:method HttpMethod/GET}))))
(expect {:request-method :post} (in (add-method (request {:method HttpMethod/POST}))))

;; add keep alive
(expect {:keep-alive true} (in (add-keep-alive (request {:keep-alive true}))))
(expect {:keep-alive false} (in (add-keep-alive (request {:keep-alive false}))))

;; add uri and query-string
(expect {:uri "/", :query-string nil} (in (add-uri-and-query (request {:uri "/"}))))
(expect {:uri "/apple", :query-string "you=me"} (in (add-uri-and-query (request {:uri "/apple?you=me"}))))
(expect {:uri "/apple", :query-string "you=me&help=me"} (in (add-uri-and-query (request {:uri "/apple?you=me&help=me"}))))

;; content-length
(expect {:content-length 5} (in (add-content-length (request {:content-length 5}))))
(expect {:content-length 15} (in (add-content-length (request {:content-length 15}))))
(expect (contains? (add-content-length (request {})) :content-length) false)

;; scheme
(expect {:scheme "http"} (in (add-scheme (request {:scheme "http"}))))
(expect {:scheme "https"} (in (add-scheme (request {:scheme "https"}))))
(expect {:scheme "http"} (in (add-scheme (request {}))))

;; character encoding
(expect {:character-encoding "whocares"} (in (add-content-encoding (request {:character-encoding "whocares"}))))
(expect {:character-encoding "behere"} (in (add-content-encoding (request {:character-encoding "behere"}))))
(expect (contains? (add-content-encoding (request {})) :character-encoding) false)

;; headers
(expect {:headers {"x-scheme" "http"}} (in (add-headers (request {:scheme "http"}))))
(expect {:headers {"content-length" "5"}} (in (add-headers (request {:content-length 5}))))

;; content-type
(expect {:content-type "foo"} (in (add-content-type (request {:content-type "foo"}))))

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
    {:request http-request})))