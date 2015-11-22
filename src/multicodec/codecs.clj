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

;; The mux codec uses a set of iternal functions to decide which codec to use
;; when encoding or decoding data.
;;
;; - `codecs` is a map from header paths to codecs.
;; - `select-encoder` takes the collection of codecs and the value to be encoded
;;   and returns the selected codec to render the value with.
;; - `select-decoder` takes the collection of codecs and the header path and
;;   returns the codec to use.
(defrecord MuxCodec
  [header codecs select-encoder select-decoder]

  mc/Encoder

  (encode!
    [this output value]
    (if-let [codec (select-encoder codecs value)]
      (write-header-encoded! (:header codec) codec output value)
      (throw (ex-info
               (str "No encoder selected for value: " (pr-str value))
               {:value value}))))


  mc/Decoder

  (decode!
    [this input]
    (let [header (mh/read-header! input)]
      (if-let [codec (select-decoder codecs header)]
        (mc/decode! codec input)
        (throw (ex-info
                 (str "No decoder selected for header: " (pr-str header))
                 {:header header}))))))


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
  (when-not (every? (comp string? :header) codecs)
    (throw (IllegalArgumentException.
             (str "Every codec must specify a header path: "
                  (pr-str (first (filter (comp complement string? :header)
                                         codecs)))))))
  (MuxCodec.
    "/multicodec"
    (into {} (map (juxt :header identity) codecs))
    (constantly (first codecs))
    get))


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
