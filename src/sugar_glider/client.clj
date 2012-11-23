(ns sugar-glider.client
    (:use lamina.core aleph.tcp aleph.formats gloss.core)
    (:import java.net.ConnectException java.lang.IllegalStateException))

(def seq-counter (atom 0))
(defn get-and-inc-seq [] (swap! seq-counter inc))

(def promise-map (atom {}))
(defn add-promise [seqnum prom]
    (swap! promise-map #(assoc % seqnum prom)))
(defn get-promise [seqnum]
    (@promise-map seqnum))
(defn del-promise [seqnum]
    (swap! promise-map #(dissoc % seqnum)))

(defn tcp-connect-fn
    "Returns a function which when called will return a result-channel of a socket
    for this given connection"
    [host port]
    #(tcp-client {:host host :port port :frame (string :utf-8 :delimiters "\n")}))

(defn on-receive 
    "When we receive any data from the glider server, we want to get the associated
    promise object, delete the promise from the global map, then deliver the return
    string to the promise. The person waiting on the promise will eval the final
    string"
    [data]
    (let [return-struct (load-string data)
          seqnum        (:seq return-struct)
          return-string (:return return-struct)
          prom          (get-promise seqnum)]
        (del-promise seqnum)
        (deliver prom return-string)))

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
        (if (nil? socket-try) (recur connect-fn) 
            (do
                (receive-all socket-try on-receive)
                socket-try))))

(defn socket-poke 
    "Given a [socket conn-fn], checks if the socket is closed. If it is, opens a new socket
    and returns a new struct with that. Otherwise, returns struct that was given"
    [agent-struct]
    (let [ socket  (first agent-struct)
           conn-fn (last  agent-struct) ]
        (if (drained? socket) 
            [(try-connect conn-fn) conn-fn]
            agent-struct)))

(defn socket-close
    "Given a [socket conn-fn], closes the socket"
    [agent-struct]
    (let [ socket (first agent-struct) ]
        (close socket)))

(defn get-socket 
    "Given a glider-agent, pokes the socket to make sure it's alive, then returns the socket
    from the agent. The poking is asynchronous, so even if the socket is closed this will
    still return the closed socket. The method using this returned socket needs to catch the
    exception which gets thrown when the socket is actually closed, and then call this
    function AGAIN to get an actual good socket.

    I'm sorry, this is bad, I should feel bad"
    [glider-struct]
    (send (:agent glider-struct) socket-poke)
    (first @(:agent glider-struct)))

(defn glider-read 
    "Given a glider-agent, attempts to synchronously read a line off of the associated socket
    
    (This isn't ever used, I wrote it for initial testing"
    [glider-struct]
    (let [ socket (get-socket glider-struct)
           socket-try (try @(read-channel socket)
                      (catch java.lang.IllegalStateException e nil)) ]
        (if (nil? socket-try) (recur glider-struct) socket-try)))

(defn glider-write
    "Given a glider-agent, attempts to synchronously write a line to the associated socket"
    [glider-struct data]
    (let [ socket (get-socket glider-struct) 
           socket-try (enqueue socket (str data)) ]
        (if (= :lamina/closed! socket-try)
            (recur glider-struct data) nil))) 


(declare glider-command)
(defn glider-connect 
    "Given a params map returns a glider connection. This is really a map of
    :agent -> reference to agent which holds the socket
    :ns -> the namespace of the socket
    
    Also handles the :env param, by running each item in :env as a command
    on the created connection (used primarily for use, require and import
    commands"
    [params]
    (let [ conn-fn (tcp-connect-fn (params :host) (params :port))
           glider-struct { :ns (params :ns) 
                           :agent (agent [(try-connect conn-fn) conn-fn]) } ]
        (doseq [env-item (params :env [])]
            (glider-command glider-struct env-item))
        glider-struct))

(defn glider-close
    "Given an agent, closes the agent's socket. Don't do this if there's still
    reads and stuff happening, they'll probably crash"
    [glider-struct]
    (send (:agent glider-struct) socket-close))

(defn glider-command
    "Given a quoted command, create a seq number and a promise, add the promise
    to the promise map with the seq number, send off the command to the server,
    then wait for the promise to be delivered. The delivery will be the string
    representation of the answer, eval it and return that"
    [glider-struct command]
    (let [seqnum (get-and-inc-seq)
          server-ns (glider-struct :ns)
          ns-command `(ns ~server-ns)
          command-struct {:seq seqnum :command (str ns-command " " command)}
          prom (promise)]
        (add-promise seqnum prom)
        (glider-write glider-struct (str command-struct))
        (load-string @prom)))

(defn glider-command-async
    "Sends a command asynchronously. Works similarly to glider-command, but doesn't
    create a seq number or wait for any promise object"
    [glider-struct command]
    (let [command-struct {:command (str command)}]
        (glider-write glider-struct (str command-struct))
        nil))

(defmacro glide [glider-struct command]
    `(glider-command ~glider-struct (quote ~command)))
(defmacro glide-async [glider-struct command]
    `(glider-command-async ~glider-struct (quote ~command)))
