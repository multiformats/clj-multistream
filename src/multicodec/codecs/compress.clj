(ns multicodec.codecs.compress
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


(defrecord CompressionCodec
  [header codec wrap-input wrap-output]

  codec/Encoder

  (encodable?
    [this value]
    (codec/encodable? codec value))


  (encode!
    [this output value]
    (codec/encode! codec (wrap-output output) value))


  codec/Decoder

  (decodable?
    [this header']
    (codec/decodable? codec header'))


  (decode!
    [this input]
    (codec/decode! codec (wrap-input input))))


(alter-meta! #'->CompressionCodec assoc :private true)
(alter-meta! #'map->CompressionCodec assoc :private true)


(defn compress-codec
  "Creates a new compression codec, wrapping the given codec. The wrapper
  functions are called on the input and output streams passed to the next
  codec.

  - `:header` string which overrides inheriting the codec's header
  - `:input` function which will transform bytes after they are encoded
  - `:output` function which will transform bytes before they are decoded"
  [codec & {:as opts}]
  (->FilterCodec
    (:header opts)
    codec
    (:input opts)
    (:output opts)))


(defn gzip-codec
  "Creates a compression codec which will apply GZIP to the encoded data."
  [codec]
  (compress-codec codec
    :header "/gzip"
    :input #(GZIPInputStream. ^InputStream %)
    :output #(GZIPOutputStream. ^OutputStream %)))
