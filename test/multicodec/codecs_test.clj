(ns multicodec.codecs-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as mc]
    [multicodec.header :as mh]
    [multicodec.codecs :as codecs])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(defn mock-codec
  [tag header]
  (reify
    clojure.lang.ILookup
    (valAt [_ k]
      (when (= :header k) header))
    mc/Encoder
    (encode! [_ output value]
      (let [content (.getBytes (pr-str value))]
        (.write output content)
        (count content)))
    mc/Decoder
    (decode! [_ input]
      [tag (slurp input)])))


(deftest header-codec
  (let [foo (mock-codec :foo "/foo")
        wrapped (codecs/wrap-headers foo)]
    (testing "codec construction"
      (is (= "/foo" (:header wrapped))
          "header should inherit wrapped codec by default") 
      (let [wrapped (codecs/wrap-headers foo "/bar")]
        (is (= "/bar" (:header wrapped))
            "header should be settable with second arg")))
    (testing "encoding roundtrip"
      (let [encoded (mc/encode wrapped 1234)]
        (is (= "/foo" (mh/read-header! (ByteArrayInputStream. encoded)))
            "should write codec header to content")
        (is (= [:foo "1234"] (mc/decode wrapped encoded))
            "should read header and delegate to codec")))
    (testing "bad header"
      (let [baos (ByteArrayOutputStream.)]
        (codecs/write-header-encoded! "/bar" foo baos :abc)
        (is (thrown? RuntimeException
                     (mc/decode wrapped (.toByteArray baos))))))))


(deftest multiplex-codec
  (testing "codec construction"
    (is (thrown? IllegalArgumentException (codecs/mux-codec))
        "construction with no codecs should throw exception")
    (is (thrown? IllegalArgumentException (codecs/mux-codec :foo))
        "construction with odd args should throw exception")
    (is (thrown? IllegalArgumentException (codecs/mux-codec :foo (mock-codec :foo nil)))
        "construction with no-header arg should throw exception"))
  (let [mux (codecs/mux-codec
              :foo (mock-codec :foo "/foo")
              :bar (mock-codec :bar "/bar"))]
    (testing "encoding with no selected codec"
      (let [mux' (assoc mux :select-encoder (constantly nil))]
        (is (thrown? RuntimeException (mc/encode! mux' nil nil))))
      (let [mux' (assoc mux :select-encoder (constantly :baz))]
        (is (thrown? RuntimeException (mc/encode! mux' nil nil)))))
    (testing "decoding with no selected codec"
      (let [mux' (assoc mux :select-decoder (constantly nil))]
        (is (thrown? RuntimeException (mc/decode! mux' nil))))
      (let [mux' (assoc mux :select-decoder (constantly :baz))]
        (let [baos (ByteArrayOutputStream.)]
          (mh/write-header! baos "/baz/")
          (is (thrown? RuntimeException
                       (mc/decode mux' (.toByteArray baos)))))))
    (testing "encoding roundtrip"
      (let [encoded (mc/encode mux 1234)]
        (is (= 10 (count encoded)) "should write the correct number of bytes")
        (is (= "/foo" (mh/read-header! (ByteArrayInputStream. encoded))))
        (is (= [:foo "1234"] (mc/decode mux encoded)))))
    (testing "no-matching decoder"
      (let [baos (ByteArrayOutputStream.)]
        (mh/write-header! baos "/baz/qux")
        (.write baos (.getBytes "abcd"))
        (is (thrown? RuntimeException
                     (mc/decode mux (.toByteArray baos))))))))


(deftest bin-codec
  (let [bin (codecs/bin-codec)
        content (byte-array 10)]
    (.nextBytes (java.security.SecureRandom.) content)
    (let [encoded (mc/encode bin content)]
      (testing "encoding"
        (is (= (count encoded) (count content))
            "should encode bytes one-to-one")
        (is (every? true? (map = content encoded))
            "should return same bytes as encoded"))
      (testing "decoding"
        (let [decoded (mc/decode bin encoded)]
          (is (= (count decoded) (count content))
              "should decode bytes one-to-one")
          (is (every? true? (map = content decoded))
              "should decode same bytes as content"))))))


(deftest text-codec
  (let [text (codecs/text-codec)
        content "the quick brown fox jumped over the lazy dog"]
    (is (= "/text/UTF-8" (:header text))
        "constructor should default to UTF-8")
    (let [encoded (mc/encode text content)]
      (testing "encoding"
        (is (= (count encoded) (count content))
            "should encode characters in bytes")
        (is (= content (slurp encoded))
            "should return same string"))
      (testing "decoding"
        (let [decoded (mc/decode text encoded)]
          (is (string? decoded)
              "should return a string")
          (is (= content decoded)
              "should decode to same string"))))))
