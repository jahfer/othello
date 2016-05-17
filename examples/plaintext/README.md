# Plaintext Example


## Development

Open a terminal and type `lein repl` to start a Clojure REPL
(interactive prompt).

In the REPL, type

```clojure
(start)
```

The call to `(start)` starts the Figwheel server at port 3449, which takes care of
live reloading ClojureScript code and CSS. A server will also be started at port 8080, which will run `server.clj`.

When you see the line `Successfully compiled "resources/public/app.js" in 21.36
seconds.`, you're ready to go. Browse to `http://localhost:8080` and enjoy.

While making changes, you are also able to type `(reset)` into your REPL to reset the document and start fresh.
