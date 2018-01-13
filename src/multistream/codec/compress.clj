(ns multistream.codec.compress
  "Compression codec which transforms the byte stream after it is encoded and
  before it is decoded by the next codec."
  (:require
    [multistream.codec :as codec :refer [defcodec]])
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

  (encode-byte-stream
    [this _ output-stream]
    (GZIPOutputStream. ^OutputStream output-stream))


  (decode-byte-stream
    [this _ input-stream]
    (GZIPInputStream. ^InputStream input-stream)))


(defn gzip-codec
  "Creates a compression codec which will apply GZIP to the encoded data."
  []
  (->GZIPCodec "/gzip/"))
