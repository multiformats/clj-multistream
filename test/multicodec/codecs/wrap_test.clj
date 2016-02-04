(ns multicodec.codecs.wrap-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.test-utils :refer [mock-codec]]
    [multicodec.codecs.wrap :as wrap])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest header-codec
  (let [foo (mock-codec :foo "/foo")
        wrapped (wrap/wrap-header foo)]
    (testing "codec construction"
      (is (= "/foo" (:header wrapped))
          "header should inherit wrapped codec by default")
      (let [wrapped (wrap/wrap-header foo "/bar")]
        (is (= "/bar" (:header wrapped))
            "header should be settable with second arg")))
    (testing "predicates"
      (is (codec/encodable? wrapped nil)
          "predicate should pass through")
      (is (codec/decodable? wrapped (:header foo))
          "wrapped codec header should be decodable"))
    (testing "encoding roundtrip"
      (let [encoded (codec/encode wrapped 1234)]
        (is (= "/foo" (header/read-header! (ByteArrayInputStream. encoded)))
            "should write codec header to content")
        (is (= [:foo "1234"] (codec/decode wrapped encoded))
            "should read header and delegate to codec")))
    (testing "bad header"
      (let [baos (ByteArrayOutputStream.)]
        (codec/encode-with-header! foo baos "/bar" :abc)
        (is (thrown? RuntimeException
                     (codec/decode wrapped (.toByteArray baos))))))))
