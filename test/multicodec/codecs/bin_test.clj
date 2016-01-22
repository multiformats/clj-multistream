(ns multicodec.codecs.bin-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as codec]
    [multicodec.codecs.bin :as bin]))


(deftest bin-codec
  (let [bin (bin/bin-codec)
        content (byte-array 10)]
    (.nextBytes (java.security.SecureRandom.) content)
    (testing "predicates"
      (is (codec/encodable? bin content)
          "byte array should be encodable")
      (is (not (codec/encodable? bin "foo"))
          "string should not be encodable")
      (is (codec/decodable? bin (:header bin))
          "bin header should be decodable")
      (is (not (codec/decodable? bin "/text"))
          "text header should not be decodable"))
    (let [encoded (codec/encode bin content)]
      (testing "encoding"
        (is (= (count encoded) (count content))
            "should encode bytes one-to-one")
        (is (every? true? (map = content encoded))
            "should return same bytes as encoded"))
      (testing "decoding"
        (let [decoded (codec/decode bin encoded)]
          (is (= (count decoded) (count content))
              "should decode bytes one-to-one")
          (is (every? true? (map = content decoded))
              "should decode same bytes as content"))))))
