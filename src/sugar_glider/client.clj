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

(defn socket-poke 
    "Given a [socket conn-fn], checks if the socket is closed. If it is, opens a new socket
    and returns a new struct with that. Otherwise, returns struct that was given"
    [agent-struct]
    (let [ socket  (first agent-struct)
           conn-fn (last  agent-struct) ]
        (if (drained? socket) 
            [(try-connect conn-fn) conn-fn]
            agent-struct)))

(defn get-socket 
    "Given a glider-agent, pokes the socket to make sure it's alive, then returns the socket
    from the agent. The poking is asynchronous, so even if the socket is closed this will
    still return the closed socket. The method using this returned socket needs to catch the
    exception which gets thrown when the socket is actually closed, and then call this
    function AGAIN to get an actual good socket.

    I'm sorry, this is bad, I should feel bad"
    [glider-agent]
    (send glider-agent socket-poke)
    (first @glider-agent))

(defn glider-read 
    "Given a glider-agent, attempts to synchronously read a line off of the associated socket"
    [glider-agent]
    (let [ socket (get-socket glider-agent) 
           socket-try (try (bytes->string @(read-channel socket))
                      (catch java.lang.IllegalStateException e nil)) ]
        (if (nil? socket-try) (recur glider-agent) socket-try)))

(defn glider-write
    "Given a glider-agent, attempts to synchronously write a line to the associated socket"
    [glider-agent data]
    (let [ socket (get-socket glider-agent) 
           socket-try (enqueue socket data) ]
        (if (= :lamina/closed! socket-try)
            (recur glider-agent data) nil))) 


(defn glider-connect 
    "Given a params map returns a glider connection. This is really a map of
    :read-fn -> a function which when called will read a line off the socket
    :write-fn -> a function which when called will write a line to the socket
    :ns -> the namespace of the socket"
    [params]
    (let [ conn-fn (tcp-connect-fn (params :host) (params :port)) ]
           (agent [(try-connect conn-fn) conn-fn])))

        
