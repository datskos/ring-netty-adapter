(ns ring.adapter.plumbing
  (:import (java.io InputStream File RandomAccessFile FileInputStream)
           (java.net URLConnection)
	         (org.jboss.netty.channel ChannelFutureListener DefaultFileRegion ChannelFutureProgressListener)
	         (org.jboss.netty.buffer ChannelBufferInputStream ChannelBuffers)
	         (org.jboss.netty.handler.stream ChunkedStream ChunkedFile)
	         (org.jboss.netty.handler.codec.http HttpHeaders HttpVersion HttpMethod HttpResponseStatus DefaultHttpResponse)))
	   
(defn- remote-address [ctx]
  (-> ctx .getChannel .getRemoteAddress .toString (.split ":") first (subs 1)))

(defn- get-meth [req]
  (-> req .getMethod .getName .toLowerCase keyword))

(defn- get-body [req]
  (ChannelBufferInputStream. (.getContent req)))

(defn- get-headers [req]
  (reduce (fn [headers name]
	    (assoc headers (.toLowerCase name) (.getHeader req name)))
	  {}
	  (.getHeaderNames req)))

(defn- content-type [headers]
  (if-let [ct (headers "content-type")]
    (-> ct (.split ";") first .trim .toLowerCase)))


(defn- uri-query [req]
  (let [uri (.getUri req)
	idx (.indexOf uri "?")]
    (if (= idx -1)
      [uri nil]
      [(subs uri 0 idx) (subs uri (inc idx))])))
    
(defn- keep-alive? [headers msg]
  (let [version (.getProtocolVersion msg)
	minor (.getMinorVersion version)
	major (.getMajorVersion version)]
    (not (or (= (headers "connection") "close")
	     (and (and (= major 1) (= minor 0))
		  (= (headers "connection") "keep-alive"))))))
  
(defn build-request-map
  "Converts a netty request into a ring request map"
  [ctx netty-request]
  (let [headers (get-headers netty-request)
	socket-address (-> ctx .getChannel .getLocalAddress)
	[uri query-string] (uri-query netty-request)]
    {:server-port        (.getPort socket-address)
     :server-name        (.getHostName socket-address)
     :remote-addr        (remote-address ctx)
     :uri                uri
     :query-string       query-string
     :scheme             (keyword (headers "x-scheme" "http"))
     :request-method     (get-meth netty-request)
     :headers            headers
     :content-type       (content-type headers)
     :content-length     (headers "content-length")
     :character-encoding (headers "content-encoding")
     :body               (get-body netty-request)
     :keep-alive         (keep-alive? headers netty-request)}))

(defn- set-headers [response headers]
  (doseq [[key val-or-vals]  headers]
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
	  :else
	  (throw (Exception. "Unrecognized body: %s" body)))))

