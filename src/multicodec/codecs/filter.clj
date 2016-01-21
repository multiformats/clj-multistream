(ns multicodec.codecs.filter
  "Logical codec which wraps another codec to transform the data before it is
  encoded or after it is decoded."
  (:require
    [multicodec.core :as codec]))


;; ## Filter Codec

(defrecord FilterCodec
  [header subcodec encoding-fn decoding-fn]

  codec/Encoder

  (encode!
    [this output value]
    (codec/encode!
      subcodec
      output
      (if encoding-fn
        (encoding-fn value)
        value)))


  codec/Decoder

  (decode!
    [this input]
    (let [value (codec/decode! subcodec input)]
      (if decoding-fn
        (decoding-fn value)
        value))))


(defn filter-codec
  "Creates a new filter codec, wrapping the given subcodec. Opts may include:

  - `:header` string which overrides inheriting the subcodec's header
  - `:encoding` function which will transform values before they are encoded
  - `:decoding` function which will transform values after they are decoded"
  [subcodec & {:as opts}]
  (FilterCodec.
    (:header opts (:header subcodec))
    subcodec
    (:encoding opts)
    (:decoding opts)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->FilterCodec)
(ns-unmap *ns* 'map->FilterCodec)
