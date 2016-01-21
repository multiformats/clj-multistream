(ns multicodec.codecs.wrap
  "Wrapper codec which writes out and reads a header before the value body."
  (:require
    [multicodec.core :as codec]
    [multicodec.header :as header]))


(defn ^:no-doc write-header-encoded!
  "Convenience function to write a header for the given codec, then use it to
  write out the encoded value."
  [codec output header value]
  (let [header-length (header/write-header! output header)
        body-length (codec/encode! codec output value)]
    (+ header-length body-length)))



;; ## Wrapper Codec

(defrecord WrapperCodec
  [header codec]

  codec/Encoder

  (encode!
    [this output value]
    (write-header-encoded! codec output header value))


  codec/Decoder

  (decode!
    [this input]
    (let [header' (header/read-header! input)]
      (when-not (= header header')
        (throw (ex-info
                 (format "The stream header %s did not match expected header %s"
                         (pr-str header') (pr-str header))
                 {:expected header, :actual header'})))
      (codec/decode! codec input))))


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
