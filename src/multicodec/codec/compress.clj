(ns multicodec.codec.compress
  "Compression codec which transforms the byte stream after it is encoded and
  before it is decoded by the next codec."
  (:require
    [multicodec.core :as codec :refer [defcodec]])
  (:import
    (java.io
      InputStream
      OutputStream)
    (java.util.zip
      GZIPInputStream
      GZIPOutputStream)))


;; ## GZIP

(defcodec GZIPCodec
  [header]

  (processable?
    [this hdr]
    (= hdr header))


  (encode-stream
    [this _ stream]
    ; TODO: assert stream is an OutputStream
    (codec/write-header! stream header)
    (GZIPOutputStream. ^OutputStream stream))


  (decode-stream
    [this _ stream]
    ; TODO: assert stream is an InputStream
    (GZIPInputStream. ^InputStream stream)))


(defn gzip-codec
  "Creates a compression codec which will apply GZIP to the encoded data."
  []
  (->GZIPCodec "/gzip/"))
