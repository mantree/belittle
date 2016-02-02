(ns belittle.generative
  (:require
   [belittle.core :refer :all]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [com.gfredericks.test.chuck.clojure-test :refer [checking]]))

;Create two dependancy functions that return tuples
;Imagine these tuple come from a remote service, so in reality can either return
;data or 'nil' if the remote service is unavailable.
(defn dependancy-one
  []
  [1 2])

(defn dependancy-two
  []
  [3 4])

;Our testing target, simply concats together the results of it's dependancies
;Recall that (= (concat nil nil) '())
(defn target
  []
  (concat (dependancy-one)
          (dependancy-two)))

;a test.check generator that returns a tuple that can contain any combination of nil and tuples
;these represent the possible returns from our dependancy functions
(def dependancy-returns-gen
  (gen/vector
   (gen/one-of
    [(gen/return nil)
     (gen/vector gen/int 2)])
   2))

;A mock creating function that binds calls to our dependant functions to return values
(defn create-mocks
  [[return-one return-two]]
  {(m dependancy-one) return-one
   (m dependancy-two) return-two})

;Test that for all combinations of our dependancies returning nil or data
;our target function always manages to return a collection, empty though it may be
(deftest target-handles-dependancies-returning-nil
  (checking
   "" 200
   [returns dependancy-returns-gen]
   (given
    (create-mocks returns)
    (is (coll? (target))))))

(target-handles-dependancies-returning-nil)
