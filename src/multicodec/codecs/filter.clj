(ns multicodec.codecs.filter
  "Logical codec which wraps another codec to transform the data before it is
  encoded or after it is decoded."
  (:require
    [multicodec.core :as codec]))


(defrecord FilterCodec
  [header codec encoding-fn decoding-fn]

  codec/Encoder

  (encodable?
    [this value]
    (codec/encodable? codec (if encoding-fn
                              (encoding-fn value)
                              value)))


  (encode!
    [this output value]
    (codec/encode!
      codec
      output
      (if encoding-fn
        (encoding-fn value)
        value)))


  codec/Decoder

  (decodable?
    [this header']
    (codec/decodable? codec header'))


  (decode!
    [this input]
    (let [value (codec/decode! codec input)]
      (if decoding-fn
        (decoding-fn value)
        value))))


(alter-meta! #'->FilterCodec assoc :private true)
(alter-meta! #'map->FilterCodec assoc :private true)


(defn filter-codec
  "Creates a new filter codec, wrapping the given codec. Opts may include:

  - `:header` string which overrides inheriting the codec's header
  - `:encoding` function which will transform values before they are encoded
  - `:decoding` function which will transform values after they are decoded"
  [codec & {:as opts}]
  (->FilterCodec
    (:header opts (:header codec))
    codec
    (:encoding opts)
    (:decoding opts)))
