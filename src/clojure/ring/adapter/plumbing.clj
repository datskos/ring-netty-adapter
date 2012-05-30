(ns ring.adapter.plumbing
  (:require [clojure.string :as s])
  (:import (java.io InputStream File RandomAccessFile FileInputStream)
           (java.net URLConnection)
           (org.jboss.netty.channel ChannelFutureListener DefaultFileRegion ChannelFutureProgressListener)
           (org.jboss.netty.buffer ChannelBufferInputStream ChannelBuffers)
           (org.jboss.netty.handler.stream ChunkedStream ChunkedFile)
           (org.jboss.netty.handler.codec.http HttpHeaders HttpVersion HttpMethod HttpResponseStatus DefaultHttpResponse HttpHeaders$Names)))

(def method-keyword (memoize (fn [^HttpMethod method]
                               (-> method .getName s/lower-case keyword))))

(defn add-method [req]
  (let [method (-> (:request req) .getMethod)]
    (assoc req :request-method (method-keyword method))))

(defn add-keep-alive [req]
  (assoc req :keep-alive (HttpHeaders/isKeepAlive (:request req))))

(defn add-uri-and-query [req]
  (let [regex #"([^?]+)[?]?([^?]+)?"
        request-uri (-> (:request req) .getUri)
        [match uri query] (re-find regex request-uri)]
    (assoc req :uri uri :query-string query)))

(defn add-content-length [req]
  (let [default-value -1
        length (HttpHeaders/getContentLength (:request req) default-value)]
    (if (= length default-value)
      req
      (assoc req :content-length length))))

(defn add-scheme [req]
  (let [scheme (HttpHeaders/getHeader (:request req) "x-scheme" "http")]
    (assoc req :scheme scheme)))

(defn add-content-encoding [req]
  (let [encoding (HttpHeaders/getHeader (:request req) HttpHeaders$Names/CONTENT_ENCODING)]
    (if (nil? encoding)
      req
      (assoc req :character-encoding encoding))))

(defn add-headers [req]
  (let [headers (.getHeaders (:request req))
        keys (map (comp s/lower-case key) headers)
        vals (map val headers)]
    (assoc req :headers (zipmap keys vals))))

(defn add-body [req]
  (let [content (-> req :request .getContent)]
    (assoc req :body (ChannelBufferInputStream. content))))

(defn- get-headers [req]
  (reduce (fn [headers name]
            (assoc headers (.toLowerCase name) (.getHeader req name)))
    {}
    (.getHeaderNames req)))

(defn- remote-address [ctx]
  (-> ctx .getChannel .getRemoteAddress .toString (.split ":") first (subs 1)))

(defn- content-type [headers]
  (if-let [ct (headers "content-type")]
    (-> ct (.split ";") first .trim .toLowerCase)))

(defn build-request-map
  "Converts a netty request into a ring request map"
  [ctx netty-request]
  (let [headers (get-headers netty-request)
        socket-address (-> ctx .getChannel .getLocalAddress)
        request {:server-port (.getPort socket-address)
                 :server-name (.getHostName socket-address)
                 :remote-addr (remote-address ctx)
                 :content-type (content-type  headers)
                 :request netty-request}]
     (-> request
       add-method
       add-keep-alive
       add-uri-and-query
       add-content-length
       add-scheme
       add-content-encoding
       add-headers
       add-body)))

(defn- set-headers [response headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val)))))

(defn- set-content-length [msg length]
  (HttpHeaders/setContentLength msg length))

(defn- write-content [ch response content keep-alive]
  (.setContent response (ChannelBuffers/copiedBuffer (.getBytes content)))
  (if keep-alive
    (do (set-content-length response (count content))
      (.write ch response))
    (-> ch (.write response) (.addListener ChannelFutureListener/CLOSE))))

(defn- write-file [ch response file keep-alive zero-copy]
  (let [raf (RandomAccessFile. file "r")
        len (.length raf)
        region (if zero-copy
      (DefaultFileRegion. (.getChannel raf) 0 len)
      (ChunkedFile. raf 0 len 8192))]
    (.setHeader response "Content-Type" (URLConnection/guessContentTypeFromName (.getName file)))
    (set-content-length response len)
    (.write ch response) ;write initial line and header
    (let [write-future (.write ch region)]
      (if zero-copy
        (.addListener write-future
          (proxy [ChannelFutureProgressListener] []
            (operationComplete [fut]
              (.releaseExternalResources region)))))
      (if not keep-alive
        (.addListener write-future ChannelFutureListener/CLOSE)))))

(defn write-response [ctx zerocopy keep-alive {:keys [status headers body]}]
  (let [ch (.getChannel ctx)
        netty-response (DefaultHttpResponse. HttpVersion/HTTP_1_1 (HttpResponseStatus/valueOf status))]
    (set-headers netty-response headers)
    (cond (string? body)
      (write-content ch netty-response body keep-alive)
      (seq? body)
      (write-content ch netty-response (apply str body) keep-alive)
      (instance? InputStream body)
      (do
        (.write ch netty-response)
        (-> (.write ch (ChunkedStream. body))
          (.addListener (proxy [ChannelFutureListener] []
                          (operationComplete [fut]
                            (.close body)
                            (-> fut .getChannel .close))))))
      (instance? File body)
      (write-file ch netty-response body keep-alive zerocopy)
      (nil? body)
      nil
      :else (throw (Exception. "Unrecognized body: %s" body)))))

