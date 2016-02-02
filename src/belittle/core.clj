(ns belittle.core
  (:require [clojure.test :as ct]
            [medley.core :refer [find-first
                                 map-keys
                                 map-vals]]))

(defmacro is->
  [target & predicates]
  (let [t (gensym)]
    `(let [~t ~target]
       ~@(map
          (fn [p]
            `(ct/is
              (~p ~t) (str '(~p ~target))))
          predicates))))

(defn arg-matcher
  [mocked called]
  (every?
   true?
   (map
    (fn [m-arg c-arg]
      ((if (fn? m-arg)
         m-arg
         (partial = m-arg)) c-arg))
    mocked
    called)))

(defn fail
  [msg]
  (ct/do-report
   {:type :fail
    :message msg})
  false)

(def anything
  (constantly true))

(defn file-and-line
  "Copied from core.test"
  ([]
   (file-and-line (new Throwable) 3))
  ([^Throwable exception depth]
   (let [stacktrace (.getStackTrace exception)]
     (if (< depth (count stacktrace))
       (let [^StackTraceElement s (nth stacktrace depth)]
         {:file (.getFileName s) :line (.getLineNumber s)})
       {:file nil :line nil}))))

(defn get-stack-trace
  []
  (.getStackTrace (Thread/currentThread)))

;Having to pass var's into both fns, solely for reporting :(
(defprotocol Mock
  (respond [this query-args bnd-var])
  (complete [this v]))

(deftype AnyTimesConsistentMock
    [response]
  Mock
  (respond [this called v]
    response)
  (complete [this v]
    (ct/do-report
     {:type :pass})))

(deftype ExactTimesConsistentMock
    [response inital-count counter-atom call-data file-line]
  Mock
  (respond [this called v]
    (swap! call-data conj [(get-stack-trace)])
    (when (<= 0 (swap! counter-atom dec))
      response))
  (complete [this v]
    (ct/do-report
     (let [c @counter-atom]
       (cond
         (zero? c) {:type :pass}
         (pos? c) (merge
                   {:type :fail
                    :expected inital-count
                    :actual (- inital-count @counter-atom)
                    :message (str "Mock under called for " v)}
                   file-line)
         (neg? c) (merge
                 {:type :fail
                  :expected inital-count
                  :actual (+ inital-count (- @counter-atom))
                  :message (str "Mock over called for " v)}
                 file-line))))))

(defn never []
  (ExactTimesConsistentMock. nil
                             0
                             (atom 0)
                             (atom [])
                             (file-and-line)))

(defn once [response]
  (ExactTimesConsistentMock. response
                             1
                             (atom 1)
                             (atom [])
                             (file-and-line)))

(defn mock
  [raw-resp]
  (if (extends? Mock (type raw-resp))
    raw-resp
    (AnyTimesConsistentMock. raw-resp)))

(defmacro m
  "Cheeky macro to aid mock returning fns"
  [f & args]
  `(list (var ~f) ~@args))

(defn wrap-arg-matcher
  [calls]
  (let [args-mocks (map #(vector ((comp rest first) %)
                                 (second %)) calls)]
    (fn [& called]
      (if-let [arg-mock (find-first
                         #(arg-matcher (first %) called)
                         args-mocks)]
        (respond (second arg-mock) called (first (ffirst calls)))
        (ct/do-report
         {:type :fail
          :message (str "Unregonised arguments to mock of " (first (ffirst calls)))
          :expected (map first args-mocks)
          :actual called})))))

(defn resolve-map-keys-to-vars
  [m]
  (if (map? m)
    (map-keys
     (fn [[h & t]]
       (let [v (cond
                 (var? h) h
                 (symbol? h) (resolve h))] ;NEED a nil check here! Var's must be resolved.
         (cons 'list (cons v t))))
     m)
    m))

(defn call-symbols-to-vars
  [element]
  (if (list? element)
    (if (= 'merge (first element))
      (map resolve-map-keys-to-vars element)
      element)
    (resolve-map-keys-to-vars element)))

(defn alter-all-var-routes
  [var->bindings]
  (doseq [[v bind] var->bindings]
    (alter-var-root v (fn [curr-fn] bind))))

(defmacro given
  [redefs-raw & body]
  (let [redefs-vared (call-symbols-to-vars redefs-raw)]
    `(let [redefs-evald# ~redefs-vared
           redefs-mocks# (map-vals mock redefs-evald#)
           previous-var-vals# (doall (for [call-var# (map ffirst redefs-mocks#)]
                                       [call-var# (var-get call-var#)]))
           var-grouped-mocks# (map-vals wrap-arg-matcher
                                        (group-by ffirst redefs-mocks#))]
       (try
         (alter-all-var-routes var-grouped-mocks#)
         ~@body
         (doseq [call-mock# redefs-mocks#]
           (complete (second call-mock#) (ffirst call-mock#)))
         (finally
           (alter-all-var-routes previous-var-vals#))))))
