(ns multicodec.codecs.wrap
  "Wrapper codec which writes out and reads a header before the value body. This
  is equivalent to calling the wrapped codec with the `encode-with-header!` and
  `decode-with-header!` functions from the core ns."
  (:require
    [multicodec.core :as codec]
    [multicodec.header :as header]))


(defrecord WrapperCodec
  [header codec]

  codec/Encoder

  (encodable?
    [this value]
    (codec/encodable? codec value))


  (encode!
    [this output value]
    (codec/encode-with-header! codec output header value))


  codec/Decoder

  (decodable?
    [this header']
    (codec/decodable? codec header'))


  (decode!
    [this input]
    (codec/decode-with-header! codec input header)))


(defn wrap-header
  "Creates a new codec which will write the header for the wrapped codec before
  calling it. When decoding, it will read the header before calling the wrapped
  codec, and throw an exception if it does not match."
  ([codec]
   (wrap-header codec (:header codec)))
  ([codec header]
   (WrapperCodec. header codec)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->WrapperCodec)
(ns-unmap *ns* 'map->WrapperCodec)
