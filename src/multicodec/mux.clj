(ns multicodec.mux
  "A multiplexing logical codec which switches between multiple internal codecs
  to read data. Uses internal functions to select codecs for reading and
  writing:

  - `codecs` is a map from header paths to codecs. Each codec should return a
    path string when looking up `:header`.
  - `select-encoder` takes the collection of codecs and the value to be encoded
    and returns the selected codec to render the value with.
  - `select-decoder` takes the collection of codecs and the header path and
    returns the codec to use."
  (:require
    [multicodec.core :as mc]))


(defrecord MuxCodec
  [codecs select-encoder select-decoder]

  mc/Encoder

  (encode!
    [this output value]
    (if-let [codec (select-encoder codecs value)]
      (let [hlen (mc/write-header! output (:header codec))
            clen (mc/encode! codec output value)]
        (+ hlen clen))
      (throw (IllegalStateException.
               (str "No encoder selected for value: " (pr-str value))))))


  mc/Decoder

  (decode!
    [this input]
    (let [header (mc/read-header! input)]
      (if-let [codec (select-decoder codecs header)]
        (mc/decode! codec input)
        (throw (IllegalStateException.
                 (str "No decoder selected for header: "
                      (pr-str header))))))))


(defn mux-codec
  "Creates a new multiplexing codec which delegates to the given collection of
  codecs by reading and writing multicodec headers when coding.

  Each codec should extend `Decoder` and provide a header path for the key
  `:header`. The first codec should also extend `Encoder`, as by default it's
  used for all encoding requests."
  [& codecs]
  (when-not (seq codecs)
    (throw (IllegalArgumentException.
             "mux-codec requires at least one codec")))
  (when-not (every? (comp string? :header) codecs)
    (throw (IllegalArgumentException.
             (str "Every codec must specify a header path: "
                  (pr-str (first (filter (comp complement string? :header)
                                         codecs)))))))
  (MuxCodec.
    (into {} (map (juxt :header identity) codecs))
    (constantly (first codecs))
    get))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->MuxCodec)
(ns-unmap *ns* 'map->MuxCodec)
