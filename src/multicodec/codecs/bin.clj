(ns multicodec.codecs.bin
  "Binary codec which simply encodes and decodes raw byte sequences."
  (:require
    [clojure.java.io :as io]
    [multicodec.core :as codec]
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayOutputStream
      Closeable
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

(defrecord BinaryEncoderStream
  [^OutputStream output]

  codec/EncoderStream

  (write!
    [this value]
    (write-bytes! value output))

  Closeable

  (close
    [this]
    (.close output)))


; TODO: option to limit the max size to read?
(defrecord BinaryDecoderStream
  [^InputStream input]

  codec/DecoderStream

  (read!
    [this]
    (let [baos (ByteArrayOutputStream.)]
      (io/copy input baos)
      (.toByteArray baos)))

  Closeable

  (close
    [this]
    (.close input)))


(defrecord BinaryCodec
  []

  codec/Codec

  (processable?
    [this hdr]
    (= header hdr))


  (encode-stream
    [this _ ctx]
    (let [output ^OutputStream (::codec/output ctx)]
      (header/write-header! output header)
      (-> ctx
          (dissoc ::codec/output)
          (assoc ::codec/encoder (BinaryEncoderStream. output)))))


  (decode-stream
    [this _ ctx]
    (let [input (::codec/input ctx)]
      (-> ctx
          (dissoc ::codec/input)
          (assoc ::codec/decoder (BinaryDecoderStream. input))))))


(alter-meta! #'->BinaryEncoderStream assoc :private true)
(alter-meta! #'->BinaryDecoderStream assoc :private true)
(alter-meta! #'->BinaryCodec assoc :private true)
(alter-meta! #'map->BinaryCodec assoc :private true)


(defn bin-codec
  "Creates a new binary codec."
  []
  (map->BinaryCodec nil))
