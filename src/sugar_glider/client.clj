(ns sugar-glider.client
    (:use lamina.core aleph.tcp aleph.formats)
    (:import java.net.ConnectException java.lang.IllegalStateException))

(defn tcp-connect-fn
    "Returns a function which when called will return a result-channel of a socket
    for this given connection"
    [host port]
    #(tcp-client {:host host :port port}))

(defn try-connect
    "Given a function which returns a result-channel of a socket, continuously tries that
    function till it successfully returns a connection, then returns that connection.
    
    We put the entire try/catch in a let so that we can later recur and try again depending
    on if the socket was successfully created or not."
    [connect-fn]
    (let [socket-try (try @(connect-fn)
                     (catch java.net.ConnectException e 
                         (println "Connection failed: " e)
                         (Thread/sleep 1000)
                         nil))]
        (if (nil? socket-try) (recur connect-fn) socket-try)))

(defn remake-socket!
    "Given a socket-ref and a function which returns a result-channel of a socket,
    swap out the current socket in the ref for a new one which for sure works"
    [socket-ref connect-fn]
    (dosync (ref-set socket-ref (try-connect connect-fn))))

(defn glider-read 
    "Attempt to synchronously read from given socket, while detecting failure. On failure 
    attempt to re-establish socket and try again"
    [socket-ref connect-fn]
    (let [socket-try (try (bytes->string @(read-channel @socket-ref))
                     (catch java.lang.IllegalStateException e
                        (remake-socket! socket-ref connect-fn)
                        nil))]
        (if (nil? socket-try) (recur socket-ref connect-fn) socket-try)))


(defn glider-connect 
    "Given a params map returns a glider connection. This is really a map of
    :read-fn -> a function which when called will read a line off the socket
    :write-fn -> a function which when called will write a line to the socket
    :ns -> the namespace of the socket"
    [params]
    (let [ connect-fn (tcp-connect-fn (params :host) (params :port))
           socket-ref (ref (try-connect connect-fn))]
           :a))
        
