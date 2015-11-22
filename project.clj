(defproject mvxcvi/multicodec "0.3.0"
  :description "Clojure implementation of the multicodec standard."
  :url "https://github.com/greglook/clj-multicodec"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :deploy-branches ["master"]

  :plugins
  [[lein-cloverage "1.0.6"]]

  :dependencies
  [[org.clojure/clojure "1.7.0"]]

  :codox
  {:metadata {:doc/format :markdown}
   :source-uri "https://github.com/greglook/clj-multicodec/blob/0.3.0/{filepath}#L{line}"
   :doc-paths [""]
   :output-path "doc/api"})
