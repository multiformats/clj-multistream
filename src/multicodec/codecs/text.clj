(ns multicodec.codecs.text
  "Text codec which uses UTF-8 to serialize strings."
  (:require
    [clojure.java.io :as io]
    [multicodec.core :as codec])
  (:import
    (java.io
      InputStream
      InputStreamReader
      OutputStream
      OutputStreamWriter
      StringWriter)
    java.nio.charset.Charset))


;; ## Text Codec

(defrecord TextCodec
  [header ^java.nio.charset.Charset charset]

  codec/Encoder

  (encodable?
    [this value]
    (string? value))


  (encode!
    [this output value]
    (let [writer (OutputStreamWriter. ^OutputStream output charset)
          content (str value)]
      (.write writer content)
      (.flush writer)
      (count (.getBytes content charset))))


  codec/Decoder

  (decodable?
    [this header']
    (= header header'))


  (decode!
    [this input]
    (let [reader (InputStreamReader. ^InputStream input charset)
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
