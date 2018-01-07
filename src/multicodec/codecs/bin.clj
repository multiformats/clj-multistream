(ns multicodec.codecs.bin
  "Binary codec which simply encodes and decodes raw byte sequences."
  (:require
    [clojure.java.io :as io]
    [multicodec.core :as codec :refer [defcodec defdecoder defencoder]])
  (:import
    (java.io
      ByteArrayOutputStream
      InputStream
      OutputStream)))


(def ^:const header "/bin/")



;; ## Encoding Protocol

(defprotocol BinaryData
  "Protocol for values which can be encoded directly as a binary sequence."

  (write-bytes!
    [data output]
    "Writes the binary data to the given output stream. Returns the number of
    bytes written."))


(extend-protocol BinaryData

  (class (byte-array 0))

  (write-bytes!
    [array ^OutputStream output]
    (.write output ^bytes array)
    (count array)))



;; ## Binary Codec

(defencoder BinaryEncoderStream
  [^OutputStream output]

  (write!
    [this value]
    (write-bytes! value output)))


; TODO: option to limit the max size to read?
(defdecoder BinaryDecoderStream
  [^InputStream input]

  (read!
    [this]
    (let [baos (ByteArrayOutputStream.)]
      (io/copy input baos)
      (.toByteArray baos))))


(defcodec BinaryCodec
  [header]

  (processable?
    [this hdr]
    (= header hdr))


  (encode-stream
    [this _ stream]
    (let [output stream]
      (codec/write-header! output header)
      (->BinaryEncoderStream output)))


  (decode-stream
    [this _ stream]
    (->BinaryDecoderStream stream)))


(defn bin-codec
  "Creates a new binary codec."
  []
  (map->BinaryCodec {:header header}))
