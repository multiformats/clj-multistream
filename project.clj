(defproject mvxcvi/multicodec "0.6.0-SNAPSHOT"
  :description "Clojure implementation of the multicodec standard."
  :url "https://github.com/greglook/clj-multicodec"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :deploy-branches ["master"]

  :plugins
  [[lein-cloverage "1.0.6"]]

  :dependencies
  [[org.clojure/clojure "1.7.0"]]

  :hiera
  {:vertical false
   :cluster-depth 2
   :ignore-ns #{clojure}
   :show-external false}

  :codox
  {:metadata {:doc/format :markdown}
   :source-uri "https://github.com/greglook/clj-multicodec/blob/develop/{filepath}#L{line}"
   :doc-paths [""]
   :output-path "doc/api"})
