(ns multicodec.codec.bin-test
  (:require
    [clojure.test :refer :all]
    [multicodec.codec.bin :as bin]
    [multicodec.core :as codec]
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest binary-codec
  (let [codec (bin/bin-codec)
        content "foo bar baz"]
    (testing "processable headers"
      (is (codec/processable? codec "/bin/"))
      (is (not (codec/processable? codec "/text/"))))
    (let [baos (ByteArrayOutputStream.)]
      (with-open [stream (codec/encode-byte-stream codec nil baos)]
        (is (satisfies? codec/EncoderStream stream))
        (is (= 11 (codec/write! stream (.getBytes content)))))
      (let [output-bytes (.toByteArray baos)]
        (is (= 18 (count output-bytes)))
        (let [input (ByteArrayInputStream. output-bytes)]
          (is (= bin/header (header/read! input)))
          (with-open [stream (codec/decode-byte-stream codec bin/header input)]
            (is (satisfies? codec/DecoderStream stream))
            (let [value (codec/read! stream)]
              (is (bytes? value))
              (is (= content (String. value))))))))))
