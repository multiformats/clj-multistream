(ns multicodec.core-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as mc]))


(deftest standard-headers
  (doseq [[codec-key path] mc/headers]
    (is (keyword? codec-key)
        "codec names should be keywords")
    (is (string? path)
        "codec paths should be strings")
    (is (.startsWith path "/")
        "codec paths should start with a slash")))
