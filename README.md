# sugar-glider

A way of executing a piece of code on a remote clojure instance and retrieving its result.

*This is not currently working! I'll put it on clojars and take off this banner*

## Usage

```clojure

(use 'sugar-glider.core)

(def connection
    (glider-connect   { :host "localhost"
                        :port 4443        }))

(glide connection (+ 1 2))
;3

(glide-async connection (+ 1 2))
;nil

```

## License

Copyright © 2012 Brian Picciano

Distributed under the Eclipse Public License, the same as Clojure.
