(ns multicodec.codecs.mux
  "Multiplexing codec which uses the codec predicates `encodable?` and
  `decodable?` to decide which codec to use when encoding or decoding data.

  `codecs` should be a map from (arbitrary) keys to codecs with headers and
  support for the codec predicates.

  The actual codec delegated to can be determined by binding
  `*dispatched-codec*` to `nil` and checking the result after an operation."
  (:require
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.wrap :as wrap]))


;; This var can be bound to find out what codec the mux used internally when
;; encoding or decoding a value.
(def ^:dynamic *dispatched-codec*)


(defn- find-encodable
  "Finds the first codec in the map which can encode the given value. Returns a
  vector of the key and codec entry, or nil if none are found."
  [codecs value]
  (first (filter #(codec/encodable? (val %) value) codecs)))


(defn- find-decodable
  "Finds the first codec in the map which can decode the given header. Returns
  a vector of the key and codec entry, or nil if none are found."
  [codecs header]
  (first (filter #(codec/decodable? (val %) header) codecs)))



;; ## Multiplexing Codec

(defrecord MuxCodec
  [header codecs]

  codec/Encoder

  (encodable?
    [this value]
    (boolean (find-encodable codecs value)))


  (encode!
    [this output value]
    (let [[codec-key codec] (find-encodable codecs value)]
      (when-not codec
        (throw (ex-info
                 (str "No codecs can encode value: " (pr-str value))
                 {:codecs (keys codecs)
                  :value value})))
      (when (bound? #'*dispatched-codec*)
        (set! *dispatched-codec* codec-key))
      (wrap/write-header-encoded! codec output (:header codec) value)))


  codec/Decoder

  (decodable?
    [this header']
    (boolean (find-decodable codecs header')))


  (decode!
    [this input]
    (let [header' (header/read-header! input)
          [codec-key codec] (find-decodable codecs header')]
      (when-not codec
        (throw (ex-info
                 (str "No codecs can decode header: " (pr-str header'))
                 {:codecs (keys codecs)
                  :header header'})))
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
                    {:codecs (keys (:codecs mux))
                     :key codec-key}))))


(defn mux-codec
  "Creates a new multiplexing codec which delegates to the given collection of
  codecs by reading and writing multicodec headers when serializing values.

  When encoding a value, the multiplexer will look for the first codec which
  reports it is `encodable?`. The selected codec's header is written first,
  then the codec is used to write the value.

  When decoding, the multiplexer tries to read a multicodec header and looks
  for the first codec which reports the header is `decodable?`. The selected
  codec is then used to read a value from the remaining data.

  As a consequence, the delegated codecs _must_ implement the codec predicates
  and _must not_ write or expect to consume their own headers!"
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
    (MuxCodec. "/multicodec" codec-map)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MuxCodec)
(ns-unmap *ns* 'map->MuxCodec)
