# No longer developed

As an alternative, please see  [aleph](https://github.com/ztellman/aleph)


## Ring-Netty-Adapter

This repo adds (experimental/alpha) Netty support to Ring

    (use 'ring.adapter.netty)

    (defn app [req]
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    "Hello World from Ring-Netty"})

    (run-netty app {:port 8080})


You can try out the demos; they are the same as the ones in the Ring repository, except here they use netty as the backend server instead of jetty.

    $ lein jar

    $ java -cp "lib/*:*" clojure.main example/hello_world.clj

    $ java -cp "lib/*:*" clojure.main example/wrapping.clj

    $ java -cp "lib/*:*" clojure.main example/linted.clj


Currently there are 2 branches: master (clojure 1.1) and compat-1.2 for clojure 1.2 support.  The only difference is proxy vs reify (reify performs better).

I'm getting roughly 7k req/s with 1.1 and 9.5k req/s with 1.2 using an unscientific benchmark on my not-quite-so-new machine.

### Leiningen

To use the netty backend, include ring-netty-adapter in your project.clj's :dependencies

    [ring-netty-adapter "0.0.3"]


Next steps:

* squash any bugs that pop up
* ssl support
* websockets, comet
