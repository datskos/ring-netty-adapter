(ns ring.adapter.plumbing
  (:require [clojure.string :as s])
  (:import (java.io InputStream File RandomAccessFile FileInputStream)
           (java.net URLConnection)
           (org.jboss.netty.channel ChannelFutureListener DefaultFileRegion ChannelFutureProgressListener)
           (org.jboss.netty.buffer ChannelBufferInputStream ChannelBuffers)
           (org.jboss.netty.handler.stream ChunkedStream ChunkedFile)
           (org.jboss.netty.handler.codec.http HttpMessage HttpHeaders HttpVersion HttpMethod HttpResponseStatus DefaultHttpResponse HttpHeaders$Names)))

(defn header
  ([req name]
    (header req name nil))
  ([req name value]
    (HttpHeaders/getHeader req name value)))

(defn content-length [^HttpMessage req]
  (let [length (HttpHeaders/getContentLength req -1)]
    (if (= length -1) nil length)))

(defn content-type [^HttpMessage req]
  (let [type (header req HttpHeaders$Names/CONTENT_TYPE "")]
    (-> type (.split ";") first s/trim s/lower-case)))

(defn headers [^HttpMessage req]
  (let [headers (.getHeaders req)
        keys (map (comp s/lower-case key) headers)
        vals (map val headers)]
    (zipmap keys vals)))

(defn add [key f]
  (fn [req]
    (let [value (f (:request req))]
      (if (nil? value) req (assoc req key value)))))

(defn add-context [key f]
  #(assoc % key (f (:context %))))

(defn remote-address [ctx]
  (-> ctx .getChannel .getRemoteAddress .toString (.split ":") first (subs 1)))

(def local-address #(-> % .getChannel .getLocalAddress))

(def method-keyword (memoize #(-> % .getName s/lower-case keyword)))
(def add-method (add :request-method #(method-keyword (.getMethod %))))
(def add-keep-alive (add :keep-alive #(HttpHeaders/isKeepAlive %)))
(def add-scheme (add :scheme #(header % "x-scheme" "http")))
(def add-headers (add :headers #(headers %)))
(def add-content-encoding (add :character-encoding #(header % HttpHeaders$Names/CONTENT_ENCODING)))
(def add-content-length (add :content-length content-length))
(def add-body (add :body #(ChannelBufferInputStream. (.getContent %))))
(def add-content-type (add :content-type content-type))
(def add-server-port (add-context :server-port #(.getPort (local-address %))))
(def add-server-name (add-context :server-name #(.getHostName (local-address %))))
(def add-remote-address (add-context :remote-addr remote-address))

(defn add-uri-and-query [req]
  (let [regex #"([^?]+)[?]?([^?]+)?"
        request-uri (-> (:request req) .getUri)
        [match uri query] (re-find regex request-uri)]
    (assoc req :uri uri :query-string query)))


(defn build-request-map
  "Converts a netty request into a ring request map"
  [ctx netty-request]
  (-> {:context ctx :request netty-request}
    add-remote-address
    add-server-name
    add-server-port
    add-method
    add-keep-alive
    add-uri-and-query
    add-scheme
    add-content-length
    add-content-encoding
    add-content-type
    add-headers
    add-body))

(defn- set-headers [response headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val)))))

(defn- write-string-body [ch response content keep-alive]
  (.setContent response (ChannelBuffers/copiedBuffer (.getBytes content)))
  (if keep-alive
    (do (HttpHeaders/setContentLength response (count content))
      (.write ch response))
    (-> ch (.write response) (.addListener ChannelFutureListener/CLOSE))))

(defn- write-file [ch response file keep-alive zero-copy]
  (let [raf (RandomAccessFile. file "r")
        len (.length raf)
        region (if zero-copy
      (DefaultFileRegion. (.getChannel raf) 0 len true)
      (ChunkedFile. raf 0 len 8192))]
    (.setHeader response "Content-Type" (URLConnection/guessContentTypeFromName (.getName file)))
    (HttpHeaders/setContentLength response len)
    (.write ch response) ;write initial line and header
    (let [write-future (.write ch region)]
      (if not keep-alive
        (.addListener write-future ChannelFutureListener/CLOSE)))))

(defn write-response [ctx zerocopy keep-alive {:keys [status headers body]}]
  (let [ch (.getChannel ctx)
        netty-response (DefaultHttpResponse. HttpVersion/HTTP_1_1 (HttpResponseStatus/valueOf status))]
    (set-headers netty-response headers)
    (cond (string? body)
      (write-string-body ch netty-response body keep-alive)
      (seq? body)
      (write-string-body ch netty-response (apply str body) keep-alive)
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

