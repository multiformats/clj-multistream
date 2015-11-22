(ns multicodec.core
  "Core multicodec protocols and functions."
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


;; ## Constants

(def headers
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
   :png "/png/"

   ; Sneak in some extras
   :edn "/edn"
   :utf8 "/text/UTF-8"})



;; ## Encoding

(defprotocol Encoder
  "An encoder converts values to binary sequences and writes the results to an
  output stream."

  (encode!
    [codec ^java.io.OutputStream output value]
    "Write the value as a sequence of bytes to the output stream. Returns the
    number of bytes written."))


(defn encode
  "Converts a value to a binary sequence and returns them as a byte array."
  ^bytes
  [codec value]
  (let [baos (ByteArrayOutputStream.)]
    (encode! codec baos value)
    (.toByteArray baos)))



;; ## Decoding

(defprotocol Decoder
  "A decoder reads binary sequences and interpretes them as Clojure values."

  (decode!
    [codec ^java.io.InputStream input]
    "Reads bytes from the input stream and returns the read value."))


(defn decode
  "Reads data from a byte array and returns the decoded value."
  [codec ^bytes byte-data]
  (let [bais (ByteArrayInputStream. byte-data)]
    (decode! codec bais)))
