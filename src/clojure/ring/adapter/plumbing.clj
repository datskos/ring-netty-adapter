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
  #(assoc % key (f (:request %))))

(defn add-context [key f]
  #(assoc % key (f (:context %))))

(defn remote-address [ctx]
  (-> ctx .getChannel .getRemoteAddress .toString (.split ":") first (subs 1)))

(def local-address #(-> % .getChannel .getLocalAddress))

(defn character-encoding [^HttpMessage request]
  (header request HttpHeaders$Names/CONTENT_ENCODING))

(def method-keyword (memoize #(-> % .getName s/lower-case keyword)))

(defn method [^HttpMessage request]
  (method-keyword (.getMethod request)))

(defn keep-alive? [^HttpMessage request]
  (HttpHeaders/isKeepAlive request))

(defn scheme [^HttpMessage request]
  (header request "x-scheme" "http"))

(defn url [^HttpMessage request]
  (let [regex #"([^?]+)[?]?([^?]+)?"
        request-uri (.getUri request)
        [match uri query] (re-find regex request-uri)]
    [uri query]))

(defn build-request-map
  "Converts a netty request into a ring request map"
  [ctx netty-request]
  (let [local-addr (local-address ctx)
        [uri query] (url netty-request)]
    { :remote-addr (remote-address ctx)
      :server-name (.getHostName (local-address ctx))
      :server-port (.getPort (local-address ctx))
      :content-type (content-type netty-request)
      :body (ChannelBufferInputStream. (.getContent netty-request))
      :content-length (content-length netty-request)
      :character-encoding (character-encoding netty-request)
      :headers (headers netty-request)
      :request-method (method netty-request)
      :keep-alive (keep-alive? netty-request)
      :scheme (scheme netty-request)
      :uri uri
      :query-string query}))

(defn- set-headers [response headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val)))))

(defn create-response-listener [keep-alive]
  (if keep-alive
    identity
    #(.addListener % ChannelFutureListener/CLOSE)))

(defn- write-string-body [ch response content listener]
  (.setContent response (ChannelBuffers/copiedBuffer (.getBytes content)))
  (HttpHeaders/setContentLength response (count content))
  (listener (.write ch response)))

(defn- write-file [ch response file listener zero-copy]
  (let [raf (RandomAccessFile. file "r")
        len (.length raf)
        region (if zero-copy
      (DefaultFileRegion. (.getChannel raf) 0 len true)
      (ChunkedFile. raf 0 len 8192))]
    (.setHeader response "Content-Type" (URLConnection/guessContentTypeFromName (.getName file)))
    (HttpHeaders/setContentLength response len)
    (.write ch response) ;write initial line and header
    (listener (.write ch region))))

(defn- write-input-stream [ch response stream listener]
  (.write ch response)
  (let [fut (.write ch (ChunkedStream. stream))]
    (listener fut)
    (.addListener (reify ChannelFutureListener
                    (operationComplete [_ _] (.close stream))))))

(defn write-response [ctx zerocopy keep-alive {:keys [status headers body]}]
  (let [listener (create-response-listener keep-alive)
        ch (.getChannel ctx)
        netty-response (DefaultHttpResponse. HttpVersion/HTTP_1_1 (HttpResponseStatus/valueOf status))]
    (set-headers netty-response headers)
    (cond (string? body)
      (write-string-body ch netty-response body listener)
      (seq? body)
      (write-string-body ch netty-response (apply str body) listener)
      (instance? InputStream body)
      (write-input-stream ch netty-response body listener)
      (instance? File body)
      (write-file ch netty-response body listener zerocopy)
      (nil? body)
      nil
      :else (throw (Exception. "Unrecognized body: %s" body)))))

