(ns belittle.core
  (:require [clojure.test :as ct]
            [slingshot.slingshot :refer [throw+]]
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
  (every? true?
          (map
           (fn [m-arg c-arg]
             ((if (fn? m-arg)
                m-arg
                (partial = m-arg)) c-arg))
           mocked
           called)))

(defn fail
  [msg]
  (with-meta
    {:state :fail}
    {:harmony/state :fail
     :harmony/fail-meta {:msg msg}}))

(def anything
  (constantly true))

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
    [response inital-count counter-atom call-data]
  Mock
  (respond [this called v]
    (swap! call-data conj [(get-stack-trace)])
    (if (<= 0 (swap! counter-atom dec))
      response
      (ct/do-report
       {:type :fail
        :expected inital-count
        :actual (+ inital-count (- @counter-atom))
        :message (str "Mock over called for " v)})))
  (complete [this v]
    (if (zero? @counter-atom)
      (ct/do-report
       {:type :pass})
      (when (pos? @counter-atom)
        (ct/do-report
         {:type :fail
          :expected inital-count
          :actual (- inital-count @counter-atom)
          :message (str "Mock under called for " v)})))))

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

(defn replace-fn-symbols-with-vars
  "Replaces first symbols with vars in map literals"
  [element]
  (if (list? element)
    (map replace-fn-symbols-with-vars element)
    (if (map? element)
      (map-keys
       (fn [[h & t]]
         (let [v (cond
                   (var? h) h
                   (symbol? h) (resolve h))]
           (cons 'list (cons v t))))
       element)
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
  (let [redefs-quoted (replace-fn-symbols-with-vars redefs-raw)]
    `(let [redef-cmds# ~redefs-quoted
           var-calls->mock# (map-vals mock redef-cmds#)
           previous-var-vals# (doall (map
                                      (fn [fn-call#]
                                            (let [fn-var# (first fn-call#)]
                                              [fn-var# (var-get fn-var#)]))
                                      (keys var-calls->mock#)))
           grouped-mocks# (map-vals wrap-arg-matcher
                                    (group-by-fn var-calls->mock#))]
       (try
         (alter-all-var-routes grouped-mocks#)
         ~@body
         (doseq [call-mock# var-calls->mock#]
           (complete (second call-mock#) (ffirst call-mock#)))
         (finally
           (alter-all-var-routes previous-var-vals#))))))
