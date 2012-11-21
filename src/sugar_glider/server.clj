(ns sugar-glider.server
    (:use lamina.core aleph.tcp aleph.formats)
    (:import java.lang.Exception))

(defn eval-string->string 
    "Given a string, tries to eval it, returns the exception as a string
    if there is one"
    [string]
    (str 
        (try (load-string string)
        (catch java.lang.Exception e 
            {:sugar-glider/exception (str e)}))))

(defn data-handler 
    "Given the channel and data-bytes tries to eval the data-bytes and
    returns the result as a string to the channel"
    [ch data-bytes]
    (let [data (bytes->string data-bytes)
          return (eval-string->string data)]
        (enqueue ch return "\n")))
        

(defn glider-listen
    "Start listen server and set up connection handler"
    [params]
    (start-tcp-server 
        (fn [ch _] (receive-all ch (partial data-handler ch)))
        {:port (:port params)}))

(defn glider-stop-listening
    "Stops the listen server"
    [glider-server]
    (glider-server)
    nil)
