(ns multicodec.codecs.text-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as codec]
    [multicodec.codecs.text :as text]))


(deftest text-codec
  (let [text (text/text-codec)
        content "the quick brown fox jumped over the lazy dog"]
    (is (= "/text/UTF-8" (:header text))
        "constructor should default to UTF-8")
    (let [encoded (codec/encode text content)]
      (testing "encoding"
        (is (= (count encoded) (count content))
            "should encode characters in bytes")
        (is (= content (slurp encoded))
            "should return same string"))
      (testing "decoding"
        (let [decoded (codec/decode text encoded)]
          (is (string? decoded)
              "should return a string")
          (is (= content decoded)
              "should decode to same string"))))))
