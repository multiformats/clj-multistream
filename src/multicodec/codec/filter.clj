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
  [header encode-fn decode-fn]

  (processable?
    [this hdr]
    (and header (= hdr header)))


  (encode-byte-stream
    [this selector output-stream]
    (when header
      (codec/write-header! output-stream header))
    output-stream)


  (encode-value-stream
    [this selector encoder-stream]
    (if encode-fn
      (->FilterEncoderStream encoder-stream encode-fn)
      encoder-stream))


  (decode-value-stream
    [this header decoder-stream]
    (if decode-fn
      (->FilterDecoderStream decoder-stream decode-fn)
      decoder-stream)))


(defn filter-codec
  "Creates a new filter codec, wrapping the given codec. Opts may include:

  - `:header`
    If set, the header will be written out before encoding further values and
    the codec will respond to `processable?` with true if the header matches.
  - `:encode-fn`
    If provided, the function will transform values before they are encoded.
  - `:decode-fn`
    If provided, the function will transform values after they are decoded."
  [& {:as opts}]
  (map->FilterCodec opts))
