(ns belittle.core-test
  (:require [clojure.test :refer :all]
            [belittle.core :refer :all]))

(def incer inc)

(deftest basic-test
  (testing "A basic mock"
    (given
     {(incer 0) (never)}
     (is
      (= (incer 0) 2)))))

(basic-test)
