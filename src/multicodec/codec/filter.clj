(ns multicodec.codec.filter
  "Logical codec which wraps another codec to transform the data before it is
  encoded or after it is decoded."
  (:require
    [multicodec.core :as codec :refer [defcodec defdecoder defencoder]]))


(defencoder FilterEncoderStream
  [stream f]

  (write!
    [this value]
    (codec/write! stream (f value))))


(defdecoder FilterDecoderStream
  [stream f]

  (read!
    [this]
    (f (codec/read! stream))))


(defcodec FilterCodec
  [encode-fn decode-fn]

  (processable?
    [this header]
    false)


  (encode-stream
    [this selector stream]
    (if encode-fn
      (->FilterEncoderStream stream encode-fn)
      stream))


  (decode-stream
    [this header stream]
    (if decode-fn
      (->FilterDecoderStream stream decode-fn)
      stream)))


(defn filter-codec
  "Creates a new filter codec, wrapping the given codec. Opts may include:

  - `:encode-fn`
    If provided, the function will transform values before they are encoded.
  - `:decode-fn`
    If provided, the function will transform values after they are decoded."
  [& {:as opts}]
  (map->FilterCodec opts))
