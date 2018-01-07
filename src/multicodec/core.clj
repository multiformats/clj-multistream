(ns multicodec.core
  "Core multicodec protocols and functions."
  (:require
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream
      InputStream)))


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



;; ## Codec Protocols

(defprotocol EncoderStream
  "A stream which can be used to encode values to some underlying byte output
  stream."

  (write!
    [stream value]
    "Write the value as a sequence of bytes to the stream. Returns the
    number of bytes written. Throws an exception if the value cannot be
    encoded."))


(defprotocol DecoderStream
  "A stream which can be used to decode values from some underlying byte input
  stream."

  (read!
    [stream]
    "Read the next value from the underlying byte stream. Returns the
    decoded value. Throws an exception if the read bytes are invalid.
    The behavior when the end of the input stream is reached is
    implementation-specific."))


(defprotocol Codec
  "A codec provides a central place for collecting options and producing new
  stream instances."

  (processable?
    [codec header]
    "Return true if this codec can process the given header.")

  (encode-stream
    [codec selector ctx]
    "Apply encoding to the given stream context. This may write multicodec
    headers to the output stream.

    The stream context map may include:

    - `::headers`
      A vector of header strings which have been written to the output.
    - `::output`
      A `java.io.OutputStream` which can write raw bytes.
    - `::encoder`
      An `EncoderStream` which can write values.

    The codec should enforce its assumptions about the context.")

  (decode-stream
    [codec header ctx]
    "Apply decoding to the given stream context. This may read and assert
    multicodec headers from the input stream.

    The stream context map may include:

    - `::headers`
      A vector of header strings read from the input.
    - `::input`
      A `java.io.InputStream` which can read raw bytes.
    - `::decoder`
      A `DecoderStream` which can read values.

    The codec should enforce its assumptions about the context."))


(defprotocol CodecFactory
  "A codec factory supports the creation of encoder and decoder streams by
  composing codecs together."

  (encoder-stream
    [codec output select-codecs]
    "Open a new encoder stream to write values to the given byte output.

    The `select-codecs` argument may be a vector of either codec keywords or
    headers processable by one of the codecs in the factory.")

  (decoder-stream
    [codec input]
    "Open a new decoding stream to read values from the given byte input."))



;; ## Multiplex Codec

(defn- select-codec
  "Select a codec from the mux by keyword or which can process this header.
  Returns a vector with the codec type key and the selected codec."
  [factory selector]
  (or (if (keyword? selector)
        (get factory selector)
        (first (filter #(processable? % selector) (vals factory)))))
      (throw (ex-info (str "No codec found for selector " (pr-str selector))
                      {:selector selector
                       :codecs (keys factory)})))


(defrecord MultiCodec
  []

  CodecFactory

  (encoder-stream
    [this output selectors]
    (let [stream (reduce (fn wrap-codec
                           [stream selector]
                           (let [codec (select-codec this selector)]
                             (encode-stream codec selector stream)))
                         output selectors)]
      (when-not (satisfies? EncoderStream stream)
        (throw (ex-info "Encoder selection did not result in an encoding stream!"
                        {:selectors selectors
                         :stream stream})))
      stream))


  (decoder-stream
    [this input]
    (loop [stream input]
      (cond
        ;; Finished building a decoder, so return.
        (satisfies? DecoderStream stream) stream

        ;; Read a header from the input to dispatch.
        (instance? InputStream stream)
          (let [header (header/read-header! stream)
                codec (select-codec this header)]
            (recur (decode-stream codec header stream)))

        :else
          (throw (ex-info "Decoder dispatch resulted in unusable stream!"
                          {:stream stream}))))))


(defn multi-codec
  "Construct a new multi-codec factory."
  [& {:as opts}]
  (map->MultiCodec opts))



;; ## Codec Utilities

(defmacro defencoder
  "Define a new encoder stream record, filling in the protocol and `Closeable`
  implementation. The first record attribute must be the wrapped stream.

  The autogenerated constructors will be private."
  [name-sym attr-vec write-form & more]
  ; TODO: assert write-form starts with (write! ...)
  `(do
     (defrecord ~name-sym
       ~attr-vec

       EncoderStream

       ~write-form

       ~@more

       java.io.Closeable

       (close
         [_]
         (.close ~(first attr-vec))))

     (alter-meta! (var ~(symbol (str "->" name-sym))) assoc :private true)
     (alter-meta! (var ~(symbol (str "map->" name-sym))) assoc :private true)))


(defmacro defdecoder
  "Define a new decoder stream record, filling in the protocol and `Closeable`
  implementation. The first record attribute must be the wrapped stream.

  The autogenerated constructors will be private."
  [name-sym attr-vec read-form & more]
  ; TODO: assert read-form starts with (read! ...)
  `(do
     (defrecord ~name-sym
       ~attr-vec

       DecoderStream

       ~read-form

       ~@more

       java.io.Closeable

       (close
         [_]
         (.close ~(first attr-vec))))

     (alter-meta! (var ~(symbol (str "->" name-sym))) assoc :private true)
     (alter-meta! (var ~(symbol (str "map->" name-sym))) assoc :private true)))


(defmacro defcodec
  "Define a new codec, filling in the protocol.

  The autogenerated constructors will be private."
  [name-sym attr-vec & more]
  `(do
     (defrecord ~name-sym
       ~attr-vec

       Codec

       ~@more)

     (alter-meta! (var ~(symbol (str "->" name-sym))) assoc :private true)
     (alter-meta! (var ~(symbol (str "map->" name-sym))) assoc :private true)))
