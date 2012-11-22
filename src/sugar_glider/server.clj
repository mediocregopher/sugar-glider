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
    "Given the channel and data-bytes, evals the data-bytes. Expects
    the value from the eval to be a map with :seq and :command keys.
    The command is another string which is evaled, with that evaluation
    turned back into a string. Sent back onto the channel is a string
    representation of a map with the same :seq and :return of the eval'd
    command (as a string)

    Ex:
    Received - {:seq :1 :command '(+ 1 1')}
    Sent     - {:seq :1 :command '2'}"
    [ch data-bytes]
    (let [data (bytes->string data-bytes)
          container (load-string data)]
        (future (enqueue ch 
            (str {:return (eval-string->string (:command container)) 
                  :seq (:seq container) }) 
            "\n"))))
        

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
