(defproject belittle "0.5.0-SNAPSHOT"
  :description "Bringing Mocking to core.test since 2016"
  :url "https://github.com/mantree/belittle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [medley "0.7.0"]]
  :profiles {:dev {:dependencies [[com.gfredericks/test.chuck "0.2.5"]]}})
