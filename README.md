# belittle

> belittle - /bɪˈlɪt(ə)l/ - To denigrate, make small of, to mock.

A Clojure library that aims to bring mocking to core.test, with enough flexibility to be used with test.check and extensibility so you can extend to meet your needs.

## !!! Early alpha - at best !!!

[![Clojars Project](http://clojars.org/belittle/latest-version.svg)](http://clojars.org/belittle)

## Appetisers

```clojure 
(:require [clojure.test :refer :all]
          [belittle.core :refer :all])

(def incer inc)
(def decer dec)

(def get-decer-0-1
  {(m decer 0) 1})
  
(deftest simple-mock
  (given
   {(incer 0) 2}
   (is
    (= (incer 0) 2))))
    
(deftest merged-mock
  (given (merge {(incer 0) 2}
                decer-0-1)
         (is (= (incer 0)
                2)
             (= (decer 0)
                1))))
```

## Description

The main contribution is the `given` macro. Fed a map of function calls to a value it rebinds the functions to mocks that return the provided value. Arg matching is provided, so only the specified args will be accepted. Arg's can also be functions, so predicates can be used instead of values. Return values can be Mock instances, to allow for restricted or changable responses.

Before `given` rebinds the vars it's first arg is evaluated. This allows the mocking map to be the product of a `merge` call, thereby allow mocks to be paramatised, grouped and resused for multiple tests. The macro `m` is provided to prevent evaluation of calls in mock producing functions.

Mocking is encapsulated by the `Mock` protocol that defines a `respond` and `complete` function. The library will aim to provide easy access to some basics (`once`, `never`, etc), but this is the extension point for any custom mocking needs you may have. `complete` is called on mocks after the tests assertions, `Mock` implementations are expected to call `core.test/do-report` with their status.

## Motivation

Besides creating a new way of structuring mock heavy tests, this libraries key motivation is to explore the combination of generative testing and mocking. For the best experience it is highly recommended to use the core.test integration provided by [test.chuck](https://github.com/gfredericks/test.chuck#alternate-clojuretest-integration). This prevents test failures being reported until after the shrinking process has been completed. 

To see this combination in action see the [generative](http://github.com/mixradio/belittle/blob/master/test/belittle/generative.clj) test namespace.

## Basic Usage

For the following examples the following is defined, tests pass unless otherwise specified:

```clojure 
(:require [clojure.test :refer :all]
          [belittle.core :refer :all])

(def incer inc)
(def decer dec)

(def decer-0-1
  {(m decer 0) 1})

(defn get-decer
  ([{:keys [x y]}]
   (get-decer x y))
  ([x y]
   {(m decer x) y}))
```

Here you can see the use of `m` to prevent the `decer` expressions being evaluated too early, `m` simply calls `var` on the first element and returns a list of the var and the args. 

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
                decer-0-1)
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

Okaydoke, what about some return constraints.

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

Only one failure is reported here as when over called the mock returns `nil`, so the second assertion passes in this example, the mock only reports the failure when `complete` is called.

## Behind the curtain

Belittle aims to be simple to use for simple use cases, and able to help for complex ones. To this end mocks can be composed together. When creating a mock, if belittle encounters a non-mock element it is wrapped to fit in. Hence the following are equilivent, in fact the first becomes the second:

``` clojure
{(incer 0) 2}

{(incer 0) (any-times (returning 2))}
```

As this shows call counting is decomplected from what to return. So you can do stuff like:

``` clojure 
(thrice (stream (cons (throwing (new Exception) (repeat 2))))
```

`stream` is a returning mock that works it's way through a collection of responses. The responses are laziness wrapped with `returning` if they are not already a mock, hence `2` can just be a plain symbol.


## License

Copyright © 2016 MixRadio

[belittle is released under the 3-clause license ("New BSD License" or "Modified BSD License")](https://github.com/mixradio/belittle/blob/master/LICENSE)

