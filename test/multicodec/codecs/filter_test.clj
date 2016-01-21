(ns multicodec.codecs.filter-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as codec]
    [multicodec.test-utils :refer [mock-codec]]
    [multicodec.codecs.filter :as filter]))


(deftest filter-codec
  (testing "without coding filters"
    (let [fltr (filter/filter-codec (mock-codec :foo "/foo"))]
      (testing "encoding roundtrip"
        (let [encoded (codec/encode fltr 1234)]
          (is (= 4 (count encoded)) "should write the correct number of bytes")
          (is (= [:foo "1234"]
                 (codec/decode fltr encoded)))))))
  (testing "with coding filters"
    (let [fltr (filter/filter-codec (mock-codec :foo "/foo")
                                    :encoding #(vector :encoded %)
                                    :decoding #(vector :decoded %))]
      (testing "encoding roundtrip"
        (let [encoded (codec/encode fltr 1234)]
          (is (= 15 (count encoded)) "should write the correct number of bytes")
          (is (= [:decoded [:foo "[:encoded 1234]"]]
                 (codec/decode fltr encoded))))))))
