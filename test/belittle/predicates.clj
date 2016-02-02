(ns belittle.predicates
  (:require [belittle.predicates :refer :all]
            [clojure.test :refer :all]))

(deftest just-works
  (testing "just passes for same length vecs"
    (is
     (just [1 2] [1 2])))
  (testing "just passes with any-order"
    (is
     (just [1 2] [2 1] :in-any-order))))

(just-works)

(deftest failing-just-tests
  (testing "just fails for different lengths"
    (is (just [1 2] [1])))
  (testing "just fails for wrong order"
    (is (just [1 2] [2 1]))))

(failing-just-tests)
