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


(deftest multiplex-codec
  (testing "codec construction"
    (is (thrown? IllegalArgumentException (codecs/mux-codec))
        "construction with no codecs should throw exception")
    (is (thrown? IllegalArgumentException (codecs/mux-codec (mock-codec :foo nil)))
        "construction with no-header arg should throw exception"))
  (testing "encoding with no selected codec"
    (let [mux (assoc (codecs/mux-codec (mock-codec :foo "/bar"))
                     :select-encoder (constantly nil))]
      (is (thrown? RuntimeException (mc/encode! mux nil nil)))))
  (let [mux (codecs/mux-codec
              (mock-codec :foo "/foo")
              (mock-codec :bar "/bar"))]
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
