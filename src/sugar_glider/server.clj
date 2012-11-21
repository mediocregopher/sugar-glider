(ns sugar-glider.server
    (:use lamina.core aleph.tcp aleph.formats)
    (:import java.lang.Exception))

(defn eval-string->string [string]
    (str 
        (try (load-string string)
        (catch java.lang.Exception e 
            {:sugar-glider/exception (str e)}))))

(defn data-handler [ch data-bytes]
    (let [data (bytes->string data-bytes)
          return (eval-string->string data)]
        (enqueue ch return "\n")))
        

(defn glider-listen
    [params]
    (start-tcp-server 
        (fn [ch _] (receive-all ch (partial data-handler ch)))
        {:port (:port params)}))

(defn glider-stop-listening
    [glider-server]
    (glider-server)
    nil)
