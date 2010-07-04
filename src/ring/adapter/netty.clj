(ns ring.adapter.netty
  (:use ring.adapter.plumbing)
  (:import (java.net InetSocketAddress)
	   (java.util.concurrent Executors)
	   (java.io ByteArrayInputStream)
	   (org.jboss.netty.bootstrap ServerBootstrap)
	   (org.jboss.netty.channel ChannelPipeline ChannelPipelineFactory Channels
				    SimpleChannelUpstreamHandler)
	   (org.jboss.netty.channel.socket.nio NioServerSocketChannelFactory)
	   (org.jboss.netty.handler.stream ChunkedWriteHandler)
	   (org.jboss.netty.handler.codec.http HttpContentCompressor HttpRequestDecoder
					       HttpResponseEncoder HttpChunkAggregator)))
	   
(defn- make-handler [handler zerocopy]
  (proxy [SimpleChannelUpstreamHandler] []
    (messageReceived [ctx evt]
		     (let [request-map (build-request-map ctx (.getMessage evt))
			   ring-response (handler request-map)]
		       (when ring-response
			 (write-response ctx zerocopy (request-map :keep-alive) ring-response))))
    (exceptionCaught [ctx evt]
		     ;(-> evt .getCause .printStackTrace)
		     (-> evt .getChannel .close))))

(defn- make-pipeline [options handler]
  (let [pipeline (Channels/pipeline)
	pipeline (doto pipeline
		  (.addLast "decoder" (HttpRequestDecoder.))
		  (.addLast "aggregator" (HttpChunkAggregator. 65636))
		  (.addLast "encoder" (HttpResponseEncoder.))
                  (.addLast "chunkedWriter" (ChunkedWriteHandler.))
		  ;(.addLast "deflater" (HttpContentCompressor.))
		  (.addLast "handler" (make-handler handler (or (:zerocopy options) false))))]
    pipeline))

(defn- pipeline-factory [options handler]
  (proxy [ChannelPipelineFactory] []
	 (getPipeline [] (make-pipeline options handler))))
	
(defn- create-server [options handler]
  (let [bootstrap (ServerBootstrap. (NioServerSocketChannelFactory.
				     (Executors/newCachedThreadPool)
				     (Executors/newCachedThreadPool)))
	bootstrap (doto bootstrap
		    (.setPipelineFactory (pipeline-factory options handler))
		    (.setOption "child.tcpNoDelay" true)
		    (.setOption "child.keepAlive" true))]
    bootstrap))
	
(defn- bind [bs port]
  (.bind bs (InetSocketAddress. port)))

(defn run-netty [handler options]
  (let [bootstrap (create-server options handler)
	port (options :port 80)]
    (println "Running server on port:" port)
    (bind bootstrap port)))

		    
