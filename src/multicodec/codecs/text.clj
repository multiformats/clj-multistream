(ns multicodec.codecs.text
  "Text codec which uses UTF-8 to serialize strings."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [multicodec.core :as codec]
    [multicodec.header :as header])
  (:import
    (java.io
      Closeable
      InputStream
      InputStreamReader
      OutputStream
      OutputStreamWriter
      StringWriter)
    java.nio.charset.Charset))


(def ^:const header-prefix
  "/text/")


(defn- header->charset
  ^Charset
  [header]
  (when (and (string? header) (not= header header-prefix))
    (Charset/forName (subs header (count header-prefix)))))


(defn- charset->header
  [charset]
  (str header-prefix charset))



;; ## Text Codec

(defrecord TextEncoderStream
  [^Charset charset
   ^OutputStreamWriter writer]

  codec/EncoderStream

  (write!
    [this value]
    (let [content (str value)]
      (.write writer content)
      (.flush writer)
      (count (.getBytes content charset))))

  Closeable

  (close
    [this]
    (.close writer)))


(defrecord TextDecoderStream
  [^Charset charset
   ^InputStreamReader reader]

  codec/DecoderStream

  (read!
    [this]
    (let [writer (StringWriter.)]
      (io/copy reader writer)
      (.toString writer)))

  Closeable

  (close
    [this]
    (.close reader)))


(defrecord TextCodec
  [^Charset default-charset]

  codec/Codec

  (processable?
    [this header]
    (str/starts-with? header header-prefix))


  (encode-stream
    [this selector ctx]
    (let [output ^OutputStream (::codec/output ctx)
          charset (or (header->charset selector) default-charset)
          header (charset->header charset)
          encoder (->TextEncoderStream
                    charset
                    (OutputStreamWriter. output charset))]
      (header/write-header! output header)
      (-> ctx
          (dissoc ::codec/output)
          (assoc ::codec/encoder encoder))))


  (decode-stream
    [this header ctx]
    (let [input ^InputStream (::codec/input ctx)
          charset (or (header->charset header) default-charset)
          header (charset->header charset)
          decoder (->TextDecoderStream
                    charset
                    (InputStreamReader. input charset))]
      (-> ctx
          (dissoc ::codec/input)
          (assoc ::codec/decoder decoder)))))


(defn text-codec
  "Creates a new text codec. If a charset is not provided, it defaults to UTF-8."
  ([]
   (text-codec (Charset/forName "UTF-8")))
  ([default-charset]
   (->TextCodec default-charset)))
