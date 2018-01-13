(ns multistream.codec.label-test
  (:require
    [clojure.test :refer :all]
    [multistream.codec :as codec]
    [multistream.codec.label :as label]
    [multistream.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest label-codec
  (let [codec (label/label-codec "/foo/")
        baos (ByteArrayOutputStream.)]
    (is (not (codec/processable? codec "/bin/")))
    (is (codec/processable? codec "/foo/"))
    (is (= :stream (codec/decode-byte-stream codec nil :stream))
        "labels have no effect on decoding stream")
    (is (= baos (codec/encode-byte-stream codec nil baos)))
    (header/write! baos "/foo/")
    (let [output-bytes (.toByteArray baos)
          input (ByteArrayInputStream. output-bytes)]
      (is (= "/foo/" (header/read! input))))))
