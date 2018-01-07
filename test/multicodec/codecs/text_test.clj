(ns multicodec.codecs.text-test
  (:require
    [clojure.test :refer :all]
    [multicodec.core :as codec]
    [multicodec.codecs.text :as text]
    [multicodec.header :as header])
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
      (with-open [stream (codec/encode-stream codec "/text/UTF-8" baos)]
        (is (satisfies? codec/EncoderStream stream))
        (is (= 44 (codec/write! stream content))))
      (let [output-bytes (.toByteArray baos)]
        (is (= 57 (count output-bytes)))
        (let [input (ByteArrayInputStream. output-bytes)]
          (is (= "/text/UTF-8" (header/read-header! input)))
          (with-open [stream (codec/decode-stream codec "/text/UTF-8" input)]
            (is (satisfies? codec/DecoderStream stream))
            (let [value (codec/read! stream)]
              (is (string? value))
              (is (= content value)))))))))
