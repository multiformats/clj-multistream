(ns multicodec.core-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [multicodec.core :as codec]))


(deftest standard-headers
  (doseq [[codec-key path] codec/headers]
    (is (keyword? codec-key)
        "codec names should be keywords")
    (is (string? path)
        "codec paths should be strings")
    (is (str/starts-with? path "/")
        "codec paths should start with a slash")))
