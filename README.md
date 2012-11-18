# sugar-glider

A way of executing a piece of code on a remote clojure instance and retrieving its result.

**This is not currently working! I'll put it on clojars and take off this banner when it is**

## Usage

**Starting the server on one instance**
```clojure
(use 'sugar-glider.core)

(def server 
    (glider-server { :port 4443 }))

(glider-server-start! server)

;(glider-server-stop! server) stops the server whenever you want
```

**Connecting to that server from another instance
```clojure
(use 'sugar-glider.core)

(def connection
    (glider-connect   { :host "localhost"
                        :port 4443        
                        :ns   'project.shared
                        :env  [ [:use 'lamina.core]
                                [:require '[aleph.http :as ahttp]]
                                [:require '[aleph.tcp :as atcp]]
                                [:import 'java.util.Date] ]}))
```

Lets look at the ```glider-connect``` params individually. First:

```clojure
{ :host "localhost"
  :port 4443
```

Pretty self explanatory; the host and port of the sugar-glider server you're connecting to

```clojure
:ns   'project.shared
```

This indicates the namespace you want the connection to "live" in on the remote node. If you haven't
already you may want to scroll down in the docs a bit to get an idea of what exactly sugar-glider
does. Basically you're going to be executing arbitrary code on the remote server; this field indicates
the namespace you want that code to run in. You can make up your own or use an existing one. Be warned,
however, that commands like ```def``` will work, so it's possible to clobber and corrupt an existing
namespace.

```clojure
:env  [ [:use 'lamina.core]
        [:require '[aleph.http :as ahttp]]
        [:require '[aleph.tcp :as atcp]]
        [:import 'java.util.Date] ]}))
```

This is the environment you want the connection to exist in. Along with the namespace specified previously,
here is where you specify all the uses/requires/imports you want that namespace to have on it. The first item
in the vectors indicates the call you want, and the rest will be sent to the corresponding function using
```apply```.

I recommend that you don't use this. If you need these, create an actual source file on the remote server 
to house the namespace and do everything in there. It's better to have everything you're going to do more 
or less pre-defined anyway, the alternative being sending whole functions over the wire, which sucks.

**Using the connection**

Any arbitrary code can be sent over and evaluated. This uses a macro on the backend to quote the entirety of
the second argument, serialize it, and send it over. The call to ```glide``` will block until the evaluated
result is returned, and returns that result directly

```clojure
(glide connection (+ 1 2))
;3
```

```glide-async``` can be used if you don't care about the return value. It will return nil immediately, the
expression to be evaluated will be added to a queue and sent in the background.

```clojure
(glide-async connection (+ 1 2))
;nil
```


## License

Copyright © 2012 Brian Picciano

Distributed under the Eclipse Public License, the same as Clojure.
