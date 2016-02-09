(defproject belittle "0.3.0-SNAPSHOT"
  :description "Bringing Mocking to core.test since 2016"
  :url "https://github.com/mixradio/belittle"
  :license {:name "New BSD License"
            :url "https://github.com/mixradio/belittle/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [medley "0.7.0"]]
  :profiles {:dev {:dependencies [[com.gfredericks/test.chuck "0.2.5"]]}})
