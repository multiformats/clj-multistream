(ns multicodec.codecs
  "Various simple and useful codec implementations.

  All codec implementations _should_ respond to `:header` with a path string
  identifying that codec."
  (:require
    [clojure.java.io :as io]
    [multicodec.core :as mc]
    [multicodec.header :as mh])
  (:import
    (java.io
      ByteArrayOutputStream
      InputStream
      InputStreamReader
      OutputStream
      OutputStreamWriter
      StringWriter)
    java.nio.charset.Charset))


(defn ^:no-doc write-header-encoded!
  "Convenience function to write a header for the given codec, then use it to
  write out the encoded value."
  [header codec output value]
  (let [header-length (mh/write-header! output header)
        body-length (mc/encode! codec output value)]
    (+ header-length body-length)))



;; ## Header Prefix

(defrecord HeaderCodec
  [header codec]

  mc/Encoder

  (encode!
    [this output value]
    (write-header-encoded! header codec output value))


  mc/Decoder

  (decode!
    [this input]
    (let [header' (mh/read-header! input)]
      (when-not (= header header')
        (throw (ex-info
                 (format "The stream header %s did not match expected header %s"
                         (pr-str header') (pr-str header))
                 {:expected header, :actual header'})))
      (mc/decode! codec input))))


(defn wrap-headers
  "Creates a new codec which will write the header for the wrapped codec before
  calling it. When decoding, it will read the header before calling the wrapped
  codec, and throw an exception if it does not match."
  ([codec]
   (wrap-headers codec (:header codec)))
  ([codec header]
   (HeaderCodec. header codec)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->HeaderCodec)
(ns-unmap *ns* 'map->HeaderCodec)



;; ## Multiplexing Codec

(defn- match-header
  "Helper function for selecting a decoder. Returns the key for the first entry
  whose codec header matches the one given."
  [codecs header]
  (some #(when (= header (:header (val %))) (key %))
        codecs))


;; The mux codec uses a set of iternal functions to decide which codec to use
;; when encoding or decoding data.
;;
;; - `codecs` is a map from keys to codecs.
;; - `select-encoder` takes the collection of codecs and the value to be encoded
;;   and returns a key identifying the codec to write the value with.
;; - `select-decoder` takes the collection of codecs and the header path and
;;   returns a key identifying the codec to read the value with.
(defrecord MuxCodec
  [header codecs select-encoder select-decoder]

  mc/Encoder

  (encode!
    [this output value]
    (if-let [codec-key (select-encoder codecs value)]
      (if-let [codec (get codecs codec-key)]
        (write-header-encoded! (:header codec) codec output value)
        (throw (ex-info
                 (str "Selected encoder " codec-key " which is not present in"
                      " the codec map " (pr-str (seq (keys codecs)))
                      " for value: " (pr-str value))
                 {:codec-keys (keys codecs)
                  :encoder codec-key
                  :value value})))
      (throw (ex-info
               (str "No encoder selected for value: " (pr-str value))
               {:codec-keys (keys codecs)
                :value value}))))


  mc/Decoder

  (decode!
    [this input]
    (let [header (mh/read-header! input)]
      (if-let [codec-key (select-decoder codecs header)]
        (if-let [codec (get codecs codec-key)]
          (mc/decode! codec input)
          (throw (ex-info
                   (str "Selected decoder " codec-key " which is not present in"
                        " the codec map " (pr-str (seq (keys codecs)))
                        " for header: " (pr-str header))
                   {:codec-keys (keys codecs)
                    :decoder codec-key
                    :header header})))
        (throw (ex-info
                 (str "No decoder selected for header: " (pr-str header))
                 {:codec-keys (keys codecs)
                  :header header}))))))


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
  (let [codec-map (apply hash-map codecs)]
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



;; ## Raw Binary

;; A simple codec which reads and writes raw binary data.
(defrecord BinaryCodec
  [header]

  mc/Encoder

  (encode!
    [this output value]
    (.write ^java.io.OutputStream output ^bytes value)
    (count value))


  mc/Decoder

  (decode!
    [this input]
    (let [baos (ByteArrayOutputStream.)]
      (io/copy input baos)
      (.toByteArray baos))))


(defn bin-codec
  "Creates a new binary codec."
  []
  (BinaryCodec. (mc/headers :bin)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->BinaryCodec)
(ns-unmap *ns* 'map->BinaryCodec)



;; ## Text

; Codec to read and write string data with a character set encoding.
(defrecord TextCodec
  [header charset]

  mc/Encoder

  (encode!
    [this output value]
    (let [writer (OutputStreamWriter. ^OutputStream output ^Charset charset)
          content (str value)]
      (.write writer content)
      (.flush writer)
      (count (.getBytes content ^Charset charset))))


  mc/Decoder

  (decode!
    [this input]
    (let [reader (InputStreamReader. ^InputStream input ^Charset charset)
          writer (StringWriter.)]
      (io/copy reader writer)
      (.toString writer))))


(defn text-codec
  "Creates a new text codec. If a charset is not provided, it defaults to UTF-8."
  ([]
   (text-codec (Charset/forName "UTF-8")))
  ([charset]
   (TextCodec. (str "/text/" charset) charset)))


;; Remove automatic constructor functions.
(ns-unmap *ns* '->TextCodec)
(ns-unmap *ns* 'map->TextCodec)
