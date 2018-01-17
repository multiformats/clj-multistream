(ns multistream.codec.transform
  "Logical codec which wraps another codec to transform the data before it is
  encoded or after it is decoded."
  (:require
    [multistream.codec :as codec :refer [defcodec defdecoder defencoder]]))


(defencoder TransformEncoderStream
  [stream f]

  (write!
    [this value]
    (codec/write! stream (f value))))


(defdecoder TransformDecoderStream
  [stream f]

  (read!
    [this]
    (f (codec/read! stream))))


(defcodec TransformCodec
  [header encode-fn decode-fn]

  (encode-value-stream
    [this selector encoder-stream]
    (if encode-fn
      (->TransformEncoderStream encoder-stream encode-fn)
      encoder-stream))


  (decode-value-stream
    [this header decoder-stream]
    (if decode-fn
      (->TransformDecoderStream decoder-stream decode-fn)
      decoder-stream)))


(defn transform-codec
  "Creates a new filter codec, identified by the given header.

  Opts may include:

  - `:encode-fn`
    If provided, the function will transform values before they are encoded.
  - `:decode-fn`
    If provided, the function will transform values after they are decoded."
  [header & {:as opts}]
  (map->TransformCodec (assoc opts :header header)))
