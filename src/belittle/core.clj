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
   (file-and-line 3))
  ([depth]
   (file-and-line (new Throwable) depth))
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

(deftype Returner
    [response]
  Mock
  (respond [this called v]
    response)
  (complete [this v]))

(deftype Thrower
    [exception]
  Mock
  (respond [this called v]
    (throw exception))
  (complete [this v]))

(deftype Stream
    [counter responders]
  Mock
  (respond [this called v]
    (try
      (let [r (nth responders @counter)]
        (swap! counter inc)
        (respond r called v))
      (catch IndexOutOfBoundsException e
        nil)))
  (complete [this v]
    (doseq [r (take @counter responders)]
      (complete r v))))

(defn returning
  [response]
  (Returner. response))

(defn throwing
  [e]
  (Thrower. e))

(defn wrap-returning
  [raw-resp]
  (if (extends? Mock (type raw-resp))
    raw-resp
    (returning raw-resp)))

(defn stream
  [responses]
  (Stream. (atom 0)
           (map wrap-returning responses)))

(deftype TimesMock
    [response inital-count counter-atom pred file-line]
  Mock
  (respond [this called v]
    (swap! counter-atom inc)
    (respond response called v))
  (complete [this v]
    (ct/do-report
     (let [c @counter-atom]
       (if (pred inital-count @counter-atom)
         {:type :pass}
         (merge {:type :fail
                 :expected inital-count
                 :actual @counter-atom
                 :message (str "Mock for " v " failed with predicate " pred)}
                file-line))))
    (complete response v)))

(deftype ReportCallers
    [call-data sub-mock]
  Mock
  (respond [this called v]
    (swap! call-data conj [(get-stack-trace)])
    (respond sub-mock called v))
  (complete [this v]
    (doseq [c @call-data]
      (ct/do-report
       {:type :fail
        :message c}))
    (complete sub-mock v)))

(defn report-callers
  [wrapped]
  (ReportCallers. (atom [])
                  (wrap-returning wrapped)))

(defn never []
  (TimesMock. (wrap-returning nil)
              0
              (atom 0)
              =
              (file-and-line)))

(defn times
  [response num]
  (TimesMock. (wrap-returning response)
              num
              (atom 0)
              =
              (file-and-line 3)))

(defn any-times
  [response]
  (TimesMock. (wrap-returning response)
              0
              (atom 0)
              (constantly true)
              (file-and-line 2)))

(defn once
  [response]
  (times (wrap-returning response) 1))

(defn twice
  [response]
  (times (wrap-returning response) 2))

(defn thrice
  [response]
  (times (wrap-returning response) 3))

(defn at-least
  [response times]
  (TimesMock. (wrap-returning response)
              times
              (atom 0)
              <=
              (file-and-line)))

(defn no-more-than
  [response times]
  (TimesMock. (wrap-returning response)
              times
              (atom 0)
              >=
              (file-and-line)))

(defn mock
  [raw-resp]
  (if (extends? Mock (type raw-resp))
    raw-resp
    (any-times raw-resp)))

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
         (merge
          {:type :fail
           :message (str "Unregonised arguments to mock of " (first (ffirst calls)))
           :expected (map first args-mocks)
           :actual called}
          (file-and-line 4)))))))

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
