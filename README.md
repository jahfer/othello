# ◐thello [![Build Status](https://travis-ci.org/jahfer/othello.svg)](https://travis-ci.org/jahfer/othello)

[![Clojars Project](http://clojars.org/othello/latest-version.svg)](http://clojars.org/othello)

A clojure/clojurescript library to handle composition and transformation of operations, using [Operational Transform](https://en.wikipedia.org/wiki/Operational_transformation) (OT).

## Usage

Below is a simplified diagram of when the use of `compose` and `transform` functions. 

When an event is triggered by the client, it is sent off to the server to be applied. Until the event is sent back from the server, the client will continue to buffer all events client-side using `compose`. 

When the event hits the server, it looks up all events that have happened since your event's parent. If there have been changes, it uses `compose` to merge them all together, and then uses `transform` to produce a modified incoming event, and modified history for the client. The server can then persist the transformed input and pass back an acknowledgement that the event was processed. 

When the client receives this acknowledgement, it flushes the buffer and sends any changes up to the server.

```
~~~~~~~~ CLIENT ~~~~~~~~|~~~~~~~~~~~~~~~~~~~~~~~~~ SERVER ~~~~~~~~~~~~~~~~~~~~~~~~~~

[event] --------->--------{compose history}-->--{event x history}--->[persist event]
        |               |                                         |    
        |               |                                         |    
        ^               v                                         v
        |               |                                         |  
        |<- {compose} <-|-----------------<------------------[event ack]

```

For a detailed explanation, [Understanding and Applying Operational Transform](http://www.codecommit.com/blog/java/understanding-and-applying-operational-transformation) is an indespensible resource.

### Basic

There are three primitive types defined in this library: "retain", "insert", and "delete". In the code they are referenced as namespaced symbols (e.g. `:othello.operations/ret` for "retain"). It's possible to expand beyond these basic building blocks, but that is experimental for now.

To create an individual operation, a factory function is provided as `othello.operations/->Op`

```clj
user> (require '[othello.operations :as ops])

user> (ops/->Op ::ops/ins "a")
;; => #othello.operations.Op{:type :othello.operations/ins, :val "a"}
```

This, of course, is a very laborious process if you wanted to build an entire description of the operation as a list. A helper method, `othello.operations/oplist`, is provided for shorthand.

```clj
user> (ops/oplist ::ops/ret 4 ::ops/ins "b")
;; => [#othello.operations.Op{:type :othello.operations/ret, :val 4} #othello.operations.Op{:type :othello.operations/ins, :val "b"}]
```

From here on out, I'll be using a shorthand to describe the operations: `{::ops/ret 2}` instead of `#othello.operations.Op{:type :othello.operations/ret, :val 2}`

### Transformation

The core principle behind the transform function defined in OT is:

`transform(a,b) = (a', b'), where apply(b', a) == apply(a', b)`

> In plain English, this means that the transform function takes two operations, one server and one client, and produces a pair of operations. These operations can be applied to their counterpart’s end state to produce exactly the same state when complete.

> ~ [Understanding and Applying Operational Transform](http://www.codecommit.com/blog/java/understanding-and-applying-operational-transformation)

A single method, `othello.transforms/transform`, is exposed to apply this transformation on lists of operations that are the same original length. For example, "retain 2, insert 1, retain 5, delete 1" has a length of 8 (7 retains + 1 delete, with inserts ignored since they weren't part of the original document).

```clj
user> (require '[othello.transforms :as xforms])

user> (def a (ops/oplist ::ops/ret 2 ::ops/ins "a"))
;; => #'user/a
user> (def b (ops/oplist ::ops/ret 2 ::ops/ins "t"))
;; => #'user/b
user> (def xf (xforms/transform a b))
;; => #'user/xf
user> (first xf)
;; => [{::ops/ret 2} {::ops/ins "a"} {::ops/ret 1}]
user> (second xf)
;; => [{::ops/ret 3} {::ops/ins "t"}]
```

A second method, `othello.transforms/compress`, is provided to minimize a list of changes into a potentially shorter list.

```clj
user> (def a (ops/oplist ::ops/ret 2 ::ops/ret 1 ::ops/ins "a" ::ops/ret 1 ::ops/ret 3))
;; => #'user/a
user> (xforms/compress a)
;; => [{::ops/ret 3} {::ops/ins "a"} {::ops/ret 4}]
```

### Composition

Composition is used to merge a list of consecutive operations into a single operation list. This is useful in situations such as buffering client-side changes.

```clj
user> (require '[othello.composers :as composers])

user> (composers/compose (ops/oplist ::ops/ins "a" ::ops/ret 1) (ops/oplist ::ops/ret 2 ::ops/ins "b"))
;; => [{::ops/ins "a"} {::ops/ret 1} {::ops/ins "b"}]
```

### Final Application

When you reach the point that you actually need to apply operations on your document, you can use `othello.documents/apply-ops`. Unfortunately, this is the least advanced and interesting function of the bunch. It takes a string and a list of operations, and outputs the new string. Of course, this breaks support for custom inputs (think Google Wave add-ons), and is something that will need to be thought through. The following works well if you're dealing with solely plaintext.

```clj
user> (require '[othello.documents :as docs])

user> (def document "ram")
;; => #'user/document
user> (def a (ops/oplist ::ops/ret 1 ::ops/ins "o" ::ops/ret 2 ::ops/ins "!"))
;; => #'user/a
user> (docs/apply-ops document a)
;; => "roam!"
```

## Custom Operations

While laborious and confusing, it is definitely possible to add custom operations to Othello. In order for it to behave as expected, your operation must install custom methods on two multimethods in the Othello library. The multimethod matches on the types of the first operation in each list. The default operations derive from `:othello.operations/operation`, which is used by several of the default methods. It's recommended that you derive your new operation from this, so it is correctly handled.

`ot.transforms/transform-ops` is the first method, and defines how the transform function should handle your operation. The method recieves three arguments: `a`, `b` and `ops'`. The first two arguments are the competing operation lists, and the third is an output vector containing the two resulting operation lists, `a'` and `b'`.

The following is an example of the methods needed for a basic `::img` operation. Several more methods are needed for all edge cases with other operations, but this provides a simple example.

```clj
(derive ::img ::ops/operation)

(defmethod othello.transforms/transform-ops [::img ::ops/operation] [a b ops']
  [(rest a) b [(conj (first ops') (first a))
               (conj (first ops') (ops/->Op ::ops/ret 1))]])
```

This follows the same rules as `:othello.operations/ins`. For the first argument, we return the tail of the list, since we're "consuming" the `::img` operation. The second argument remains untouched since we didn't inspect it at all. The final argument is the most interesting; this is our "application" of the transformation on both sides. For the side that is receiving our new `::img` operation, we want to pass it along, and we do so by `conj`ing the `::img` operation onto the beginning of `ops'`. For the side that sent our new operation, we just need to retain over it, and treat it as a length of `1`.

The next method we need to implement is `othello.composers/compose-ops`, which takes a similar list of inputs/output, except that `out` is a single operations list, rather than a vector of two. This is intuitive if you consider the goal of composition is to take multiple operations and merge them into a single operation.

```clj
(defmethod othello.composers/compose-ops [::img ::ops/ret] [a b out]
  (let [b (if (= 1 (get-in (vec b) [0 :val]))
               (rest b)
               (update-in (vec b) [0 :val] dec))]
    [(rest a) b (conj out (first a))]))

(defmethod othello.composers/compose-ops [::img ::ops/del] [a b out]
  [(rest a) (rest b) out])
```

In the first method in the above code, we define a composition of our `::img`, and `::othello.operations/ret`. In case the retain is more than one unit long, we want to be careful to consume only a single unit for our `::img` operation. It's important to note taht we're making sure to cast `b` to a vec before calling `get-in` on it. This is because the collection may turn into a list depending on the operations before it.

In the second method, we "apply" the delete by passing back the tail of the two operation lists, and don't `conj` anything to the `out`, since the delete action cancelled out our `::img` insertion.

**Note: The order of operations is important; `[::img ::ops/ret]` is _not_ the same as `[::ops/ret ::img]`. The second type is the operation applied after the first operation.**

Applying the above code lets us use our new `::img` operation like any other:

```clj
user> (compose (ops/oplist ::ops/ret 1 ::img "http://google.com/logo.png") (ops/oplist ::ops/ret 2 ::ops/ins "b"))
;; => [{::ops/ret 1} {::img "http://google.com/logo.png"} {::ops/ins "b"}]
```

```clj
user> (def a (ops/oplist ::ops/ret 1 ::img "http://google.com/logo.png"))
;; => #'user/a
user (def b (ops/oplist ::ops/ret 1 ::ops/ins "b"))
;; => #'user/b
user> (def xf (xforms/transform a b))
;; => #'user/xf
user> (first xf)
;; => [{::ops/ret 1} {::img "http://google.com/logo.png} {::ops/ret 1}]
user> (second xf)
;; => [{::ops/ret 2} {::ops/ins "b"}]
```

## License

Copyright © 2015 Jahfer Husain

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
