(ns multistream.codec.text-test
  (:require
    [clojure.test :refer :all]
    [multistream.codec :as codec]
    [multistream.codec.text :as text]
    [multistream.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest text-codec
  (let [codec (text/text-codec)
        content "the quick brown fox jumped over the lazy dog"]
    (testing "processable headers"
      (is (not (codec/processable? codec "/bin/")))
      (is (codec/processable? codec "/text/"))
      (is (codec/processable? codec "/text/UTF-8"))
      (is (codec/processable? codec "/text/US-ASCII")))
    (let [baos (ByteArrayOutputStream.)]
      (header/write! baos (codec/select-header codec :text))
      (with-open [stream (codec/encode-byte-stream codec :text baos)]
        (is (satisfies? codec/EncoderStream stream))
        (is (= 44 (codec/write! stream content))))
      (let [encoded (.toByteArray baos)]
        (is (= 57 (count encoded)))
        (let [input (ByteArrayInputStream. encoded)]
          (is (= "/text/UTF-8" (header/read! input)))
          (with-open [stream (codec/decode-byte-stream codec "/text/UTF-8" input)]
            (is (satisfies? codec/DecoderStream stream))
            (let [value (codec/read! stream)]
              (is (string? value))
              (is (= content value)))))))))
