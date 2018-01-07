(ns multicodec.codec.label-test
  (:require
    [clojure.test :refer :all]
    [multicodec.codec.label :as label]
    [multicodec.core :as codec]
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest label-codec
  (let [codec (label/label-codec "/foo/")
        baos (ByteArrayOutputStream.)]
    (is (not (codec/processable? codec "/bin/")))
    (is (codec/processable? codec "/foo/"))
    (is (= :stream (codec/decode-stream codec nil :stream))
        "labels have no effect on decoding stream")
    (is (= baos (codec/encode-stream codec nil baos)))
    (let [output-bytes (.toByteArray baos)
          input (ByteArrayInputStream. output-bytes)]
      (is (= "/foo/" (header/read! input))))))
