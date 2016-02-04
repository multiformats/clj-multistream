(ns multicodec.core
  "Core multicodec protocols and functions."
  (:require
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


;; ## Constants

(def headers
  "Map of codec keywords to header paths, drawn from the multicodec standards
  document."
  {:bin    "/bin/"  ; raw binary
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
   :png "/png/"

   ; Sneak in some extras
   :edn "/edn"
   :utf8 "/text/UTF-8"})



;; ## Encoding

(defprotocol Encoder
  "An encoder converts values to binary sequences and writes the results to an
  output stream."

  (encodable?
    [codec value]
    "Returns true if the value type can be encoded by this codec.")

  (encode!
    [codec ^java.io.OutputStream output value]
    "Write the value as a sequence of bytes to the output stream. Returns the
    number of bytes written. Throws an exception if the value cannot be
    encoded."))


(defn encode
  "Converts a value to a binary sequence and returns them as a byte array."
  ^bytes
  [codec value]
  (let [baos (ByteArrayOutputStream.)]
    (encode! codec baos value)
    (.toByteArray baos)))


(defn encode-with-header!
  "Writes a header to the output stream, then writes out the encoded value.
  Returns the number of bytes written.

  If a header is not given, the codec's header is used."
  ([codec output value]
   (encode-with-header! codec output (:header codec) value))
  ([codec output header value]
   (let [header-length (header/write-header! output header)
         body-length (encode! codec output value)]
     (+ header-length body-length))))



;; ## Decoding

(defprotocol Decoder
  "A decoder reads binary sequences and interpretes them as Clojure values."

  (decodable?
    [codec header]
    "Returns true if the codec supports decoding of the given header.")

  (decode!
    [codec ^java.io.InputStream input]
    "Reads bytes from the input stream and returns the read value. May not fully
    consume the stream if multiple items are present. Throws an exception if the
    input is malformed or incomplete."))


(defn decode
  "Reads data from a byte array and returns the decoded value."
  [codec ^bytes byte-data]
  (let [bais (ByteArrayInputStream. byte-data)]
    (decode! codec bais)))


(defn decode-with-header!
  "Reads a multicodec header from the input stream and checks it against the one
  provided. Throws an exception if the header does not match, otherwise uses the
  codec to read a value from the stream.

  If a header is not given, the codec's header is used."
  ([codec input]
   (decode-with-header! codec input (:header codec)))
  ([codec input header]
   (let [header' (header/read-header! input)]
     (when-not (= header header')
       (throw (ex-info
                (format "The stream header %s did not match expected header %s"
                        (pr-str header') (pr-str header))
                {:expected header, :actual header'})))
     (decode! codec input))))
