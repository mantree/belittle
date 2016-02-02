# belittle

> belittle - /bɪˈlɪt(ə)l/ - To denigrate, make small of, to mock.

A Clojure library that aims to bring mocking to core.test, with enough flexibility to be used with test.check and extensibility so you can roll to your own needs.

## !!! Early alpha - at best !!!

[![Clojars Project](http://clojars.org/belittle/latest-version.svg)](http://clojars.org/belittle)

The main contribution is the `given` macro. Fed a map of function calls to values it rebinds the functions to mocks that return the provided value. Arg matching is provided, such that only valid args will accepted. Arg's can also be functions, allowing predicates to be used instead of values. Return values can be Mock instances, to allow for restricted or changable responses.

Before `given` preforms the var binding the body is evaluated. This allows the mocking map to be the product of a `merge` call, thereby allow mocks to be paramatised and resused for multiple tests. The macro `m` is provided to prevent evaluation of those functions results.

Mocking is provided by the `Mock` protocol that defines a `respond` and `complete` function. The library will aim to provide easy access to some basics (`once`, `never`, etc), but this is the extension point for any custom mocking needs you may have. `complete` is called on all mocks after the body of assertions, `Mock` implementations are expected to call `core.test/do-report` with their status.

Besides creating a new way of structuring mock heavy tests, this libraries key motivation is to explore the idea of generative mocking tests and to see if they can be a useful construct. For the best experience it is highly recommended to use the modified [test.check](https://github.com/clojure/test.check) integration provided by [test.chuck](https://github.com/gfredericks/test.chuck#alternate-clojuretest-integration). This prevents test failures being reported until after the shrinking process has been completed. A small example of this being put together is made below

## Usage

For the following examples the following is defined:

```clojure 
(:require [clojure.test :refer :all]
          [belittle.core :refer :all])

(def incer inc)

(def get-decer-0-1
  {(m decer 0) 1})

(defn get-decer
  ([{:keys [x y]}]
   (get-decer x y))
  ([x y]
   {(m decer x) y}))
```

Here you can see the use of `m` to prevent `decer` being evaluated too early, `m` simply calls `var` on the first element and return a list of the var and the args. 

Let's bind incer, when called with 0, to return 2.

```clojure 
(deftest simple-mock
  (given
   {(incer 0) 2}
   (is
    (= (incer 0) 2))))
```

Let's pull in our fixed mock for decer.

```clojure 
(deftest fixed-mock
  (given (merge {(incer 0) 2}
                get-decer-0-1)
         (is (= (incer 0)
                2)
             (= (decer 0)
                1))))
```

Let's adjust our decer mock on the fly.

```clojure 
(deftest dynamic-mock
  (given (merge {(incer 0) 2}
                (get-decer 0 1))
         (is (= (incer 0)
                2)
             (= (decer 0)
                1))))
```

Let's check the mocks args can be replaced with predicates.

```clojure 
(deftest predicate-mock
  (given
   {(incer anything) 2}
   (is
    (= (incer "foo") 2))))
```

Okaydoke, what about completion checks on mocks.

```clojure 
(deftest under-call
  (given
   {(incer 0) (once 2)}))
```

The following is reported:
```
Mock under called for #'belittle.core-test/incer
expected: 1
  actual: 0
```

Hows about I over call something.

```clojure 
(deftest over-call
  (given
   {(incer 0) (once 2)}
   (is
    (= (incer 0) 2))
   (is
    (= (incer 0) nil))))
```

Reports:
```
Mock over called for #'belittle.core-test/incer
expected: 1
  actual: 2
```

Only one failure is reported here as when over called the mock returns `nil`, so the second assertion passes in this example, but the mock fails when `complete` is called.


## test.check

One of the key motivations for this library is to explore whether combining test.checks generators with mock producing functions is a useful. This motivation comes from building highly connected micro-services and the need to test for certain behaviours across all of a given services dependancies.

For examples exploring this idea see the [generative](http://github.com/mixradio/belittle/blob/master/test/belittle/generative.clj) test namespace.
 

## License

Copyright © 2016 MixRadio

[belittle is released under the 3-clause license ("New BSD License" or "Modified BSD License")](https://github.com/mixradio/belittle/blob/master/LICENSE)

