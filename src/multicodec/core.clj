(ns multicodec.core
  "Core multicodec protocols and functions."
  (:require
    [multicodec.header :as header])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream
      InputStream
      OutputStream)))


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

  (encode-byte-stream
    [codec selector output-stream]
    "Apply encoding to the given stream of bytes. The stream should be a
    `java.io.OutputStream` instance. This method should return either another
    `OutputStream` or an `EncoderStream`.

    This may write multicodec headers to the stream.")

  (encode-value-stream
    [codec selector encoder-stream]
    "Wrap encoding logic around the values produced by the given encoder stream.")

  (decode-byte-stream
    [codec header input-stream]
    "Apply decoding to the given stream of bytes. The stream should be a
    `java.io.InputStream` instance. This method should return either another
    `InputStream` or a `DecoderStream`.")

  (decode-value-stream
    [codec header decoder-stream]
    "Wrap decoding logic around the values produced by the given decoder stream."))


(defprotocol CodecFactory
  "A codec factory supports the creation of encoder and decoder streams by
  composing codecs together."

  (encoder-stream
    ^java.io.Closeable
    [factory output selectors]
    "Open a new encoder stream to write values to the given byte output.

    The `selectors` argument may be a vector of either codec keywords or
    headers processable by one of the codecs in the factory.")

  (decoder-stream
    ^java.io.Closeable
    [factory input]
    "Open a new decoding stream to read values from the given byte input."))



;; ## Multiplex Codec

(defn- select-codec
  "Select a codec from the mux by keyword or which can process this header.
  Returns a vector with the codec type key and the selected codec."
  [factory selector]
  (or (if (keyword? selector)
        (get factory selector)
        (first (filter #(processable? % selector) (vals factory))))
      (throw (ex-info (str "No codec found for selector " (pr-str selector))
                      {:selector selector
                       :codecs (keys factory)}))))


(defrecord MuxCodecFactory
  []

  CodecFactory

  (encoder-stream
    [this output selectors]
    (when-not (instance? OutputStream output)
      (throw (ex-info "The output argument to encoder-stream must be a java.io.OutputStream"
                      {:output output
                       :selectors selectors})))
    (let [[stream codecs]
          (reduce (fn wrap-bytes
                    [[stream codecs] selector]
                    (let [codec (select-codec this selector)]
                      [(encode-byte-stream codec selector stream)
                       (conj codecs codec)]))
                  [output []]
                  selectors)]
      (when-not (satisfies? EncoderStream stream)
        (throw (ex-info "Encoder selection did not result in an encoder stream!"
                        {:selectors selectors
                         :stream stream})))
      (reduce (fn wrap-values
                [stream [selector codec]]
                (encode-value-stream codec selector stream))
              stream
              (reverse (map vector selectors codecs)))))


  (decoder-stream
    [this input]
    (when-not (instance? InputStream input)
      (throw (ex-info "The input argument to decoder-stream must be a java.io.InputStream"
                      {:input input})))
    (loop [stream input
           headers []
           codecs []]
      (cond
        ;; Finished building a decoder, so return.
        (satisfies? DecoderStream stream)
          (reduce (fn wrap-values
                    [stream [header codec]]
                    (decode-value-stream codec header stream))
                  stream
                  (reverse (map vector headers codecs)))

        ;; Read a header from the input to dispatch.
        (instance? InputStream stream)
          (let [header (header/read! stream)
                codec (select-codec this header)]
            (recur (decode-byte-stream codec header stream)
                   (conj headers header)
                   (conj codecs codec)))

        :else
          (throw (ex-info "Decoder dispatch resulted in unusable stream!"
                          {:stream stream
                           :headers headers}))))))


(alter-meta! #'->MuxCodecFactory assoc :private true)
(alter-meta! #'map->MuxCodecFactory assoc :private true)


(defn mux
  "Construct a new multiplexing codec factory."
  [& {:as opts}]
  (map->MuxCodecFactory opts))



;; ## Codec Utilities

(defn write-header!
  "Writes a multicodec header for `path` to the given stream. Returns the
  number of bytes written."
  [stream header]
  (header/write! stream header))


(defmacro defencoder
  "Define a new encoder stream record, filling in the protocol and `Closeable`
  implementation. The first record attribute must be the wrapped stream.

  The autogenerated constructors will be private."
  [name-sym attr-vec write-form & more]
  ; TODO: assert write-form starts with (write! ...)
  `(do
     (defrecord ~name-sym
       ~(if (:tag (meta (first attr-vec)))
          attr-vec
          (update attr-vec 0 vary-meta assoc :tag 'java.io.Closeable))

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
       ~(if (:tag (meta (first attr-vec)))
          attr-vec
          (update attr-vec 0 vary-meta assoc :tag 'java.io.Closeable))

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
  (let [[method-forms more] (split-with list? more)
        codec-methods (into {} (map (juxt first identity)) method-forms)
        default-method #(get codec-methods %1 (cons %1 %2))]
    `(do
       (defrecord ~name-sym
         ~attr-vec

         Codec

         ~(default-method 'processable?
            `([this# header#]
              (= header# (:header this#))))

         ~(default-method 'encode-byte-stream
            `([this# selector# output-stream#]
              output-stream#))

         ~(default-method 'encode-value-stream
            `([this# selector# encoder-stream#]
              encoder-stream#))

         ~(default-method 'decode-byte-stream
            `([this# header# input-stream#]
              input-stream#))

         ~(default-method 'decode-value-stream
            `([this# header# decoder-stream#]
              decoder-stream#))

         ~@more)

       (alter-meta! (var ~(symbol (str "->" name-sym))) assoc :private true)
       (alter-meta! (var ~(symbol (str "map->" name-sym))) assoc :private true))))
