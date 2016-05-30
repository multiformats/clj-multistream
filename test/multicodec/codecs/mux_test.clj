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
    (is (mux/mux-codec :foo (mock-codec :foo nil))
        "construction with no-header arg should not throw exception"))
  (let [mux (mux/mux-codec
              :foo (mock-codec :foo "/foo")
              :bar (mock-codec :bar "/bar"))]
    (testing "encodable predicate"
      (is (false? (codec/encodable? mux ^{:foo false, :bar false} {})))
      (is (true? (codec/encodable? mux ^{:foo true, :bar false} #{})))
      (is (true? (codec/encodable? mux ^{:foo false, :bar true} []))))
    (testing "encoding with no supported codec")
      (is (thrown? Exception (codec/encode! mux nil ^{:foo false, :bar false} {})))
    (testing "encoding"
      (binding [mux/*dispatched-codec* nil]
        (let [encoded (codec/encode mux {:abc 123})]
          (is (= :foo mux/*dispatched-codec*))))
      (binding [mux/*dispatched-codec* nil]
        (let [encoded (codec/encode mux ^{:foo false} {:abc 123})]
          (is (= :bar mux/*dispatched-codec*)))))
    (testing "decodable predicate"
      (is (false? (codec/decodable? mux "/bin")))
      (is (true? (codec/decodable? mux "/foo")))
      (is (true? (codec/decodable? mux "/bar"))))
    (testing "decoding with no supported codec"
      (binding [mux/*dispatched-codec* nil]
        (let [baos (ByteArrayOutputStream.)]
          (header/write-header! baos "/baz")
          (is (thrown? RuntimeException
                       (codec/decode mux (.toByteArray baos)))
              "should throw an exception"))
        (is (nil? mux/*dispatched-codec*))))
    (testing "decoding"
      (binding [mux/*dispatched-codec* nil]
        (let [encoded (codec/encode mux {:abc 123})]
          (is (= :foo mux/*dispatched-codec*))
          (set! mux/*dispatched-codec* nil)
          (let [decoded (codec/decode mux encoded)]
            (is (= [:foo "{:abc 123}"] decoded))
            (is (= :foo mux/*dispatched-codec*))))))
    (testing "codec selection"
      (is (thrown? RuntimeException (mux/select mux :baz))
          "should throw exception when selecting missing codec")
      (binding [mux/*dispatched-codec* nil]
        (let [encoded (codec/encode (mux/select mux :bar) 'abc-123)]
          (is (= [:bar "abc-123"] (codec/decode mux encoded))
              "should force writing with selected codec")
          (is (= :bar mux/*dispatched-codec*)))))))
