(ns multicodec.codecs.bin
  "Binary codec which simply encodes and decodes raw byte sequences."
  (:require
    [clojure.java.io :as io]
    [multicodec.core :as codec])
  (:import
    (java.io
      ByteArrayOutputStream
      InputStream
      OutputStream)))


;; ## Encoding Protocol

(defprotocol BinaryData
  "Protocol for values which can be encoded directly as a binary sequence."

  (write-bytes!
    [data output]
    "Writes the binary data to the given output stream. Returns the number of
    bytes written."))


(defn binary?
  "Helper function which returns true for values which satisfy the `BinaryData`
  protocol and are valid encoding values."
  [value]
  (satisfies? BinaryData value))


(extend-protocol BinaryData

  (class (byte-array 0))

  (write-bytes!
    [array ^OutputStream output]
    (.write output ^bytes array)
    (count array)))



;; ## Binary Codec

(defrecord BinaryCodec
  [header]

  codec/Encoder

  (encode!
    [this output value]
    (write-bytes! value output))


  codec/Decoder

  (decode!
    [this input]
    (let [baos (ByteArrayOutputStream.)]
      (io/copy input baos)
      (.toByteArray baos))))


(defn bin-codec
  "Creates a new binary codec."
  []
  (BinaryCodec. (codec/headers :bin)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->BinaryCodec)
(ns-unmap *ns* 'map->BinaryCodec)
