(ns multicodec.core
  "Core multicodec definitions and methods."
  (:require
    [clojure.string :as str])
  (:import
    (java.io
      IOException
      InputStream
      OutputStream)
    java.nio.charset.Charset))


(def ^:no-doc ^:const max-header-length
  "The maximum length (in bytes) a header path can be."
  127)


(def ^:no-doc ^java.nio.charset.Charset header-charset
  "The character set that codec headers are encoded with."
  (Charset/forName "UTF-8"))


(def paths
  "Map of codec keywords to header paths, drawn from the multicodec standards
  document."
  {:binary "/bin/"  ; raw binary
   :base2  "/b2/"   ; ascii base-2 (binary)
   :hex    "/b16/"  ; ascii base-16 (hexadecimal)
   :base32 "/b32/"  ; ascii base-32
   :base58 "/b58/"  ; ascii base-58
   :base64 "/b64/"  ; ascii base-64

   ; JSONs
   :json   "/json/"
   :cbor   "/cbor/"
   :bson   "/bson/"
   :bjson  "/bjson/"
   :ubjson "/ubjson/"

   ; Protobuf
   :protobuf "/protobuf/"  ; Protocol Buffers
   :capnp    "/capnp/"     ; Cap-n-Proto
   :flatbuf  "/flatbuf/"   ; FlatBuffers

   ; Archives
   :tar "/tar/"
   :zip "/zip/"

   ; Images
   :png "/png/"})



;; ## Encoding

(defn encode-header
  "Returns the byte-encoding of the header path.

  The path is trimmed and has a newline appended to it before encoding."
  ^bytes
  [path]
  (let [path-bytes (-> path (str/trim) (str "\n") (.getBytes header-charset))
        length (count path-bytes)]
    (when (> length max-header-length)
      (throw (IllegalArgumentException.
               (format "Header paths longer than %d bytes are not supported: %s has %d"
                       max-header-length (pr-str path) length))))
    (let [encoded (byte-array (inc length))]
      (aset-byte encoded 0 (byte length))
      (System/arraycopy path-bytes 0 encoded 1 length)
      encoded)))


(defn write-header!
  "Writes a multicodec header for `path` to the given stream. Returns the number
  of bytes written."
  [^OutputStream output path]
  (let [header (encode-header path)]
    (.write output header)
    (count header)))



;; ## Decoding

(defn- take-bytes!
  "Attempts to read `length` bytes from the given stream. Returns a byte array with
  the read bytes."
  ^bytes
  [^InputStream input length]
  (let [content (byte-array length)]
    (loop [offset 0
           remaining length]
      (let [n (.read input content offset remaining)]
        (if (< n remaining)
          (recur (+ offset n) (- remaining n))
          content)))))


(defn read-header!
  "Attempts to read a multicodec header from the given stream. Returns the
  header path read. Throws an IOException if the stream does not have a valid
  header or there is an error reading from the stream."
  ^String
  [^InputStream input]
  (let [length (.read input)]
    (when-not (< length 128)
      (throw (IOException.
               (format "First byte in stream is not a valid header length: %02x"
                       length))))
    (let [header (String. (take-bytes! input length) header-charset)]
      (when-not (.endsWith header "\n")
        (throw (IOException.
                 (str "Last byte in header is not a newline: "
                      (pr-str (.charAt header (dec (count header))))))))
      (str/trim-newline header))))



;; ## Codec Protocols

(defprotocol Encoder
  "An encoder converts values to binary sequences and writes the results to an
  output stream."

  (encode!
    [codec ^java.io.OutputStream output value]
    "Write the value as a sequence of bytes to the output stream. Returns the
    number of bytes written."))


(defprotocol Decoder
  "A decoder reads binary sequences and interpretes them as Clojure values."

  (decode!
    [codec ^java.io.InputStream input]
    "Reads bytes from the input stream and returns the read value."))


; TODO: `encode` and `decode` which operate on byte arrays
