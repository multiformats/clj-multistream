(ns multicodec.codecs.mux
  "Multiplexing codec which uses a set of iternal functions to decide which
  codec to use when encoding or decoding data.

  - `codecs` is a map from keys to codecs.
  - `select-encoder` takes the collection of codecs and the value to be encoded
    and returns a key identifying the codec to write the value with.
  - `select-decoder` takes the collection of codecs and the header path and
    returns a key identifying the codec to read the value with.

  By default, the selection functions will look for codecs which can encode and
  decode the values and headers passed to the mux codec."
  (:require
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.wrap :as wrap]))


(defn find-encodable
  "Finds a codec in the map which can encode the given value. Returns the key
  for the codec, or nil if none are found."
  [codecs value]
  (ffirst (filter #(codec/encodable? (val %) value) codecs)))


(defn find-decodable
  "Finds a codec in the map which can decode the given header. Returns the key
  for the codec, or nil if none are found."
  [codecs header]
  (ffirst (filter #(codec/decodable? (val %) header) codecs)))


(defn- resolve-codec!
  "Returns the selected codec, if it exists. Otherwise, throws an exception."
  [codecs selected value]
  (when-not selected
    (throw (ex-info
             (str "No codec selected for: " (pr-str value))
             {:codecs (keys codecs)
              :value value})))
  (when-not (get codecs selected)
    (throw (ex-info
             (str "Selected codec " selected " which is not present in"
                  " the codec map " (pr-str (seq (keys codecs)))
                  " for: " (pr-str value))
             {:codecs (keys codecs)
              :selected selected
              :value value})))
  (get codecs selected))



;; ## Multiplexing Codec

;; This var can be bound to find out what codec the mux used internally when
;; encoding or decoding a value.
(def ^:dynamic *dispatched-codec*)


(defrecord MuxCodec
  [header codecs select-encoder select-decoder]

  codec/Encoder

  (encodable?
    [this value]
    (boolean (when-let [codec (get codecs (select-encoder codecs value))]
               (codec/encodable? codec value))))


  (encode!
    [this output value]
    (let [codec-key (select-encoder codecs value)
          codec (resolve-codec! codecs codec-key value)]
      (when (bound? #'*dispatched-codec*)
        (set! *dispatched-codec* codec-key))
      (wrap/write-header-encoded! codec output (:header codec) value)))


  codec/Decoder

  (decodable?
    [this header']
    (boolean (when-let [codec (get codecs (select-decoder codecs header))]
               (codec/decodable? codec header))))


  (decode!
    [this input]
    (let [header (header/read-header! input)
          codec-key (select-decoder codecs header)
          codec (resolve-codec! codecs codec-key header)]
      (when (bound? #'*dispatched-codec*)
        (set! *dispatched-codec* codec-key))
      (codec/decode! codec input))))


(defn select
  "Convenience function for selecting a specific codec from a multiplexer. The
  returned codec will encode and decode as the mux would, but only for that
  subcodec."
  [mux codec-key]
  (if-let [codec (get (:codecs mux) codec-key)]
    (wrap/wrap-header codec)
    (throw (ex-info (str "Multiplexer does not contain codec for key "
                         (pr-str codec-key) " " (pr-str (keys (:codecs mux))))
                    {:codec-keys (keys (:codecs mux))
                     :key codec-key}))))


(defn mux-codec
  "Creates a new multiplexing codec which delegates to the given collection of
  codecs by reading and writing multicodec headers when coding.

  By default, the first codec given is used for all encoding. The codec's
  `:header` is written first, then the codec is used to write the value.

  When decoding, the multiplexer tries to read a multicodec header and looks up
  the corresponding codec in the `codecs` map. The codec is used to read a
  value from the remaining data.

  As a consequence, the delegated codecs _must not_ write or expect to consume
  their own headers!"
  [& codecs]
  (when-not (seq codecs)
    (throw (IllegalArgumentException.
             "mux-codec requires at least one codec")))
  (when-not (even? (count codecs))
    (throw (IllegalArgumentException.
             "mux-codec must be given an even number of arguments")))
  (let [codec-map (apply array-map codecs)]
    (when-let [bad-codecs (seq (remove (comp string? :header)
                                       (vals codec-map)))]
      (throw (IllegalArgumentException.
               (str "Every codec must specify a header path: "
                    (pr-str bad-codecs)))))
    (MuxCodec.
      "/multicodec"
      codec-map
      find-encodable
      find-decodable)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MuxCodec)
(ns-unmap *ns* 'map->MuxCodec)
