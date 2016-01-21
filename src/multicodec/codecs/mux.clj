(ns multicodec.codecs.mux
  "Multiplexing codec which uses a set of iternal functions to decide which
  codec to use when encoding or decoding data.

  - `codecs` is a map from keys to codecs.
  - `select-encoder` takes the collection of codecs and the value to be encoded
    and returns a key identifying the codec to write the value with.
  - `select-decoder` takes the collection of codecs and the header path and
    returns a key identifying the codec to read the value with."
  (:require
    [multicodec.core :as codec]
    [multicodec.header :as header]
    [multicodec.codecs.wrap :as wrap]))


;; ## Multiplexing Codec

;; This var can be bound to find out what codec the mux used internally when
;; encoding or decoding a value.
(def ^:dynamic *dispatched-codec*)


(defrecord MuxCodec
  [header codecs select-encoder select-decoder]

  codec/Encoder

  (encode!
    [this output value]
    (let [codec-key (select-encoder codecs value)
          codec (get codecs codec-key)]
      (when-not codec-key
        (throw (ex-info
                 (str "No encoder selected for value: " (pr-str value))
                 {:codec-keys (keys codecs)
                  :value value})))
      (when-not codec
        (throw (ex-info
                 (str "Selected encoder " codec-key " which is not present in"
                      " the codec map " (pr-str (seq (keys codecs)))
                      " for value: " (pr-str value))
                 {:codec-keys (keys codecs)
                  :encoder codec-key
                  :value value})))
      (when (bound? #'*dispatched-codec*)
        (set! *dispatched-codec* codec-key))
      (wrap/write-header-encoded! codec output (:header codec) value)))


  codec/Decoder

  (decode!
    [this input]
    (let [header (header/read-header! input)
          codec-key (select-decoder codecs header)
          codec (get codecs codec-key)]
      (when-not codec-key
        (throw (ex-info
                 (str "No decoder selected for header: " (pr-str header))
                 {:codec-keys (keys codecs)
                  :header header})))
      (when-not codec
        (throw (ex-info
                 (str "Selected decoder " codec-key " which is not present in"
                      " the codec map " (pr-str (seq (keys codecs)))
                      " for header: " (pr-str header))
                 {:codec-keys (keys codecs)
                  :decoder codec-key
                  :header header})))
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


(defn- match-header
  "Helper function for selecting a decoder. Returns the key for the first entry
  whose codec header matches the one given."
  [codecs header]
  (some #(when (= header (:header (val %))) (key %))
        codecs))


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
      (constantly (first codecs))
      match-header)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MuxCodec)
(ns-unmap *ns* 'map->MuxCodec)
