(defproject mvxcvi/multicodec "0.2.0-SNAPSHOT"
  :description "Clojure implementation of the multicodec standard."
  :url "https://github.com/greglook/clj-multicodec"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :deploy-branches ["master"]

  :dependencies
  [[org.clojure/clojure "1.7.0"]]

  :codox
  {:metadata {:doc/format :markdown}
   :source-uri "https://github.com/greglook/clj-multicodec/blob/master/{filepath}#L{line}"
   :doc-paths [""]
   :output-path "doc/api"})
