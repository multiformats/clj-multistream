(ns multicodec.mux-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as multicodec]
    [multicodec.mux :refer [mux-codec]])
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
    multicodec/Encoder
    (encode! [_ output value]
      (let [content (.getBytes (pr-str value))]
        (.write output content)
        (count content)))
    multicodec/Decoder
    (decode! [_ input]
      [tag (slurp input)])))


(deftest multiplex-codec
  (testing "codec construction"
    (is (thrown? IllegalArgumentException (mux-codec))
        "construction with no codecs should throw exception")
    (is (thrown? IllegalArgumentException (mux-codec (mock-codec :foo nil)))
        "construction with no-header arg should throw exception"))
  (testing "encoding with no selected codec"
    (let [mux (assoc (mux-codec (mock-codec :foo "/bar"))
                     :select-encoder (constantly nil))]
      (is (thrown? IllegalStateException (multicodec/encode! mux nil nil)))))
  (let [mux (mux-codec
              (mock-codec :foo "/foo")
              (mock-codec :bar "/bar"))]
    (testing "encoding roundtrip"
      (let [baos (ByteArrayOutputStream.)
            out-len (multicodec/encode! mux baos 1234)
            encoded (.toByteArray baos)]
        (is (= 10 out-len) "should write the correct number of bytes")
        (is (= "/foo" (multicodec/read-header! (ByteArrayInputStream. encoded))))
        (is (= [:foo "1234"] (multicodec/decode! mux (ByteArrayInputStream. encoded))))))
    (testing "no-matching decoder"
      (let [baos (ByteArrayOutputStream.)]
        (multicodec/write-header! baos "/baz/qux")
        (.write baos (.getBytes "abcd"))
        (is (thrown? IllegalStateException
                     (multicodec/decode! mux (ByteArrayInputStream. (.toByteArray baos)))))))))
