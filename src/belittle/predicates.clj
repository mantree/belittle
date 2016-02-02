(ns belittle.predicates
  (:require [clojure.test :as ct]
            [clojure.data :refer [diff]]))

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
         (do-report {:type :pass, :message ~msg,
                     :expected e#, :actual a#})
         (do-report {:type :fail, :message ~msg,
                     :expected e#, :actual a#
                     :diffs [[a# (take 2 (diff a# e#))]]}))
       (do-report {:type :fail, :message "Count mismatch",
                   :expected e#, :actual a#
                   :diffs [[a# (take 2 (diff a# e#))]]}))))

(defmethod ct/assert-expr 'just [msg [_ a e & opts]]
  (just- msg a e opts))

(defmethod ct/assert-expr 'belitte.predicates/just [msg [_ a e & opts]]
  (just- msg a e opts))
