(ns belittle.predicates
  (:require [clojure.test :as ct]
            [clojure.data :refer [diff]]))

(defn report-
  ([type msg a e]
   (report- type msg a e nil))
  ([type msg a e diffs]
   (ct/do-report (merge
               {:type type :message msg
                :expected e :actual a}
               (when diffs
                 {:diffs [[a (take 2 diffs)]]})))))

(declare just)

(defn just-
  "Basic just. Not as sophisticated as Midje's"
  [msg act exp opts]
  `(let [a# ~act
         e# ~exp
         ec# (count e#)
         ac# (count a#)]
     (if (= ac# ec#)
       (if (or (= a# e#)
               (and (= :in-any-order (first '~opts))
                    (every? true?
                            (for [i# e#]
                              (some #(= % i#) a#)))))
         (report- :pass ~msg a# e#)
         (report- :fail ~msg a# e# (diff a# e#)))
       (report- :fail ~msg a# e# (diff a# e#)))))

(defmethod ct/assert-expr 'just [msg [_ a e & opts]]
  (just- msg a e opts))

(defmethod ct/assert-expr 'belitte.predicates/just [msg [_ a e & opts]]
  (just- msg a e opts))
