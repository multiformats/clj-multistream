(ns multicodec.codecs.mux-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.test-utils :refer [mock-codec]]
    [multicodec.codecs.mux :as mux])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest multiplex-codec
  (testing "codec construction"
    (is (thrown? IllegalArgumentException (mux/mux-codec))
        "construction with no codecs should throw exception")
    (is (thrown? IllegalArgumentException (mux/mux-codec :foo))
        "construction with odd args should throw exception")
    (is (thrown? IllegalArgumentException (mux/mux-codec :foo (mock-codec :foo nil)))
        "construction with no-header arg should throw exception"))
  (let [mux (mux/mux-codec
              :foo (mock-codec :foo "/foo")
              :bar (mock-codec :bar "/bar"))]
    (testing "encoding with no selected codec"
      (let [mux' (assoc mux :select-encoder (constantly nil))]
        (is (thrown? RuntimeException (codec/encode! mux' nil nil))))
      (let [mux' (assoc mux :select-encoder (constantly :baz))]
        (is (thrown? RuntimeException (codec/encode! mux' nil nil)))))
    (testing "decoding with no selected codec"
      (let [mux' (assoc mux :select-decoder (constantly nil))]
        (is (thrown? RuntimeException (codec/decode! mux' nil))))
      (let [mux' (assoc mux :select-decoder (constantly :baz))]
        (let [baos (ByteArrayOutputStream.)]
          (header/write-header! baos "/baz/")
          (is (thrown? RuntimeException
                       (codec/decode mux' (.toByteArray baos)))))))
    (testing "codec selection"
      (is (thrown? RuntimeException (mux/select mux :baz))
          "should throw exception when selecting missing codec")
      (binding [mux/*dispatched-codec* nil]
        (let [encoded (codec/encode (mux/select mux :bar) 'abc-123)]
          (is (= [:bar "abc-123"] (codec/decode mux encoded))
              "should force writing with selected codec")
          (is (= :bar mux/*dispatched-codec*)))))
    (testing "encoding roundtrip"
      (binding [mux/*dispatched-codec* nil]
        (let [encoded (codec/encode mux 1234)]
          (is (= :foo mux/*dispatched-codec*)
              "encoding should set dispatched-codec var")
          (is (= 10 (count encoded)) "should write the correct number of bytes")
          (is (= "/foo" (header/read-header! (ByteArrayInputStream. encoded))))
          (set! mux/*dispatched-codec* nil)
          (is (= [:foo "1234"] (codec/decode mux encoded)))
          (is (= :foo mux/*dispatched-codec*)
              "decoding should set dispatched-codec var"))))
    (testing "no-matching decoder"
      (let [baos (ByteArrayOutputStream.)]
        (header/write-header! baos "/baz/qux")
        (.write baos (.getBytes "abcd"))
        (is (thrown? RuntimeException
                     (codec/decode mux (.toByteArray baos))))))))
