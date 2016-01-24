(ns belittle.core
  (:require [clojure.test :as ct]
            [slingshot.slingshot :refer [throw+]]
            [medley.core :refer [find-first]]))

(defn arg-matcher
  [mocked called]
  (every? true?
          (map
           (fn [m-arg c-arg]
             ((if (fn? m-arg)
                m-arg
                (partial = m-arg)) c-arg))
           mocked
           called)))

(def anything
  (constantly true))

(defn get-stack-trace
  []
  (.getStackTrace (Thread/currentThread)))

(defprotocol Mock
  (respond [this query-args])
  (complete [this]))

(deftype AnyTimesConsistentMock
    [response]
  Mock
  (respond [this called]
    response)
  (complete [this]
    {:type :pass}))

(deftype ExactTimesConsistentMock
    [response inital-count counter-atom call-data]
  Mock
  (respond [this called]
    (swap! call-data conj [(get-stack-trace)])
    (if (<= 0 (swap! counter-atom dec))
      response
      (ct/do-report {:type :fail        ;maybe have :fail-mock?
                     :mock-data {:expected inital-count
                                 :actual (inc inital-count)
                                 :call-data @call-data
                                 :type :over-called}})))
  (complete [this]
    (ct/do-report
     {:type (if (= 0 @counter-atom)
              :pass :fail)
      :mock-data {:expected inital-count
                  :actual (- inital-count @counter-atom)
                  :call-data @call-data
                  :type (if (= 0 @counter-atom)
                          :complete :under-called)}})))

(defn never []
  (ExactTimesConsistentMock. nil
                             0
                             (atom 0)
                             (atom [])))

(defn once [response]
  (ExactTimesConsistentMock. response
                             1
                             (atom 1)
                             (atom [])))

(defn mock
  [raw-resp]
  (if (extends? Mock (type raw-resp))
    raw-resp
    (AnyTimesConsistentMock. raw-resp)))

(defmacro m
  "Cheeky macro to enable mock composition"
  [f & args]
  `(list (var ~f) ~@args))

(defn wrap-arg-matcher
  [calls]
  (let [args-mocks (map #(vector ((comp rest first) %)
                                 (mock (second %))) calls)]
    (fn [& called]
      (if-let [arg-mock (find-first
                         #(arg-matcher (first %) called)
                         args-mocks)]
        (respond (second arg-mock) called)
        (ct/do-report
         {:type :fail
          :expected args-mocks
          :actual called
          :mock-data {:type :bad-args}})))))

(defn quote-map-keys
  [element]
  (if (list? element)
    (map quote-map-keys element)
    (if (map? element)
      (zipmap (map (fn [[h & t]]
                     (let [v (cond
                               (var? h) h
                               (symbol? h) (resolve h))]
                       (cons 'list (cons v t))))
                   (keys element))
              (vals element))
      element)))

(defn collapse-bk
  [raw]
  (if (map? raw)
     raw
     (eval (map quote-map-keys raw))))

(def group-by-fn (partial group-by (fn [[k v]] (first k))))
(def mapcat-v (comp vec mapcat))

(defn alter-all-var-routes
  [var->bindings]
  (doseq [[v bind] var->bindings]
    (alter-var-root v (fn [curr-fn] bind))))

(defmacro given
  [redefs-raw & body]
  (let [redefs-quoted (quote-map-keys redefs-raw)]
    `(let [redef-cmds# ~redefs-quoted
           ;push these larger blocks out into functions, to aid debugging if nothing else!
           redef-vared# (map-keys
                         (fn [fn-call#]
                           (let [fn-p# (first fn-call#)
                                 fn-var# (if (var? fn-p#)
                                           fn-p#
                                           (resolve fn-p#))]
                             (cons fn-var# (rest fn-call#))))
                         redef-cmds#)
           var-calls->mock# (map-vals mock redef-vared#)
           previous-var-vals# (doall (map
                                      (fn [fn-call#]
                                            (let [fn-var# (first fn-call#)]
                                              [fn-var# (var-get fn-var#)]))
                                      (keys var-calls->mock#)))
           grouped-mocks# (map-vals wrap-arg-matcher (group-by-fn var-calls->mock#))]
       (try
         (alter-all-var-routes grouped-mocks#)
         ;construct a seq combining checker and mock report maps.
         ~@body
         (finally
           (alter-all-var-routes previous-var-vals#))))))
