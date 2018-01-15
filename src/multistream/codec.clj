(ns multistream.codec
  "Core multistream codec protocols and functions."
  (:require
    [multistream.header :as header])
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
    "Write the value as a sequence of bytes to the stream. Returns the number
    of bytes the value was encoded to, though this may be different than the
    final bytes written.

    This method should throw an exception if the value cannot be written."))


(defprotocol DecoderStream
  "A stream which can be used to decode values from some underlying byte input
  stream."

  (read!
    [stream]
    "Read the next value from the underlying byte stream. Returns the
    decoded value, or throws an exception on error.

    If the end of the stream has been reached, this method should return the
    value of the `:eof` attribute on the stream (if present), or throw an
    ex-info with `:type :multistream.codec/eof`."))


(defprotocol Codec
  "A codec provides a central place for collecting options and producing new
  stream instances."

  (processable?
    [codec header]
    "Return true if this codec can process the given header.")

  (select-header
    [codec selector]
    "Return the header string to use for this codec. Often, this will be the
    same as the result of `(:header codec)`.")

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
    [factory selectors output]
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


(defn- wrap-byte-encoders
  "Wrap byte-encoding logic around the output stream for each selected codec.
  Returns a tuple of the sequence of headers in the order written, and codecs
  in the order used, and the wrapped stream,."
  [factory selectors output]
  (loop [stream output
         selectors selectors
         headers []
         codecs []]
    (if (seq selectors)
      ; Select and wrap next codec.
      (let [selector (first selectors)
            codec (select-codec factory selector)
            header (select-header codec selector)]
        (header/write! stream header)
        (recur (encode-byte-stream codec selector stream)
               (next selectors)
               (conj headers header)
               (conj codecs codec)))
      ; Done wrapping, return stream info.
      [headers codecs stream])))


(defn- wrap-byte-decoders
  "Wrap byte-decoding logic around the input stream for each selected codec.
  Returns a tuple of the sequence of headers in the order read, the selected
  codecs, and the wrapped stream."
  [factory input]
  (loop [stream input
         headers []
         codecs []]
    (cond
      ; Finished building a decoder, so return.
      (satisfies? DecoderStream stream)
        [headers codecs stream]

      ; Read a header from the input to dispatch.
      (instance? InputStream stream)
        (let [header (header/read! stream)
              codec (select-codec factory header)]
          (recur (decode-byte-stream codec header stream)
                 (conj headers header)
                 (conj codecs codec)))

      :else
        (throw (ex-info "Decoder dispatch resulted in unusable stream!"
                        {:stream stream
                         :headers headers})))))


(defn- wrap-value-stream
  "Wrap value-transform logic around the codec stream for the given sequence of
  selected headers and codecs. Returns the wrapped stream.

  Codecs are wrapped in reverse order, and each stream passed to a codec will
  have the list of headers _following_ that codec inserted with the
  `:multistream.codec/headers` key."
  [codec-fn headers codecs encoder]
  (loop [stream encoder
         headers (reverse headers)
         codecs (reverse codecs)]
    (if (seq codecs)
      ; Wrap next codec.
      (let [header (first headers)
            codec (first codecs)]
        (recur (assoc (codec-fn codec header stream)
                      ::headers (cons header (::headers stream)))
               (next headers)
               (next codecs)))
      ; Done wrapping, return stream.
      stream)))


(defrecord MultiCodecFactory
  []

  CodecFactory

  (encoder-stream
    [this selectors output]
    (when-not (instance? OutputStream output)
      (throw (ex-info "The output argument to encoder-stream must be a java.io.OutputStream"
                      {:output output
                       :selectors selectors})))
    (let [[headers codecs stream] (wrap-byte-encoders this selectors output)]
      (when-not (satisfies? EncoderStream stream)
        (throw (ex-info "Encoder selection did not result in an encoder stream!"
                        {:selectors selectors
                         :headers headers
                         :stream stream})))
      (wrap-value-stream encode-value-stream headers codecs stream)))


  (decoder-stream
    [this input]
    (when-not (instance? InputStream input)
      (throw (ex-info "The input argument to decoder-stream must be a java.io.InputStream"
                      {:input input})))
    (apply wrap-value-stream
           decode-value-stream
           (wrap-byte-decoders this input))))


(alter-meta! #'->MultiCodecFactory assoc :private true)
(alter-meta! #'map->MultiCodecFactory assoc :private true)


(defn multi
  "Construct a new multiplexing codec factory."
  [& {:as opts}]
  (map->MultiCodecFactory opts))


(defn encode
  "Encodes the given value using either a direct codec or a multicodec factory
  and the given selectors. Returns a byte array containing the encoded value,
  or throws an exception on error."
  ([codec value]
   (encode (multi :codec codec) [:codec] value))
  ([factory selectors value]
   (let [baos (ByteArrayOutputStream.)]
     (with-open [encoder (encoder-stream factory selectors baos)]
       (write! encoder value))
     (.toByteArray baos))))


(defn decode
  "Decodes the given byte array using either a direct codec or a multicodec
  factory."
  [codec-or-factory input]
  (let [bais (ByteArrayInputStream. ^bytes input)]
    (if (satisfies? CodecFactory codec-or-factory)
      ; Use factory to decode against headers.
      (with-open [decoder (decoder-stream codec-or-factory bais)]
        (read! decoder))
      ; Use direct codec.
      (let [codec codec-or-factory
            header (header/read! bais)]
        (when-not (processable? codec header)
          (throw (ex-info (str "Expected processable codec header but read "
                               (pr-str header))
                          {:header header})))
        (with-open [decoder (->> bais
                                 (decode-byte-stream codec header)
                                 (decode-value-stream codec header))]
          (read! decoder))))))



;; ## Codec Implementation Utilities

(defmacro defencoder
  "Define a new encoder stream record, filling in the protocol and `Closeable`
  implementation. The first record attribute must be the wrapped stream.

  The autogenerated constructors will be private."
  [name-sym attr-vec write-form & more]
  (when (or (not (list? write-form))
            (not= 'write! (first write-form)))
    (throw (IllegalArgumentException.
             "First method form in defencoder must be: (write! ...)")))
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
  (when (or (not (list? read-form))
            (not= 'read! (first read-form)))
    (throw (IllegalArgumentException.
             "First method form in defdecoder must be: (read! ...)")))
  `(do
     (defrecord ~name-sym
       ~(if (:tag (meta (first attr-vec)))
          attr-vec
          (update attr-vec 0 vary-meta assoc :tag 'java.io.Closeable))

       DecoderStream

       ~read-form

       ~@more

       ; TODO: Seqable
       ; TODO: IReduceInit

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
        method* #(get codec-methods %1 (cons %1 %2))]
    `(do
       (defrecord ~name-sym
         ~attr-vec

         Codec

         ~(method* 'processable?
            `([this# header#]
              (= header# (:header this#))))

         ~(method* 'select-header
            `([this# selector#]
              (:header this#)))

         ~(method* 'encode-byte-stream
            `([this# selector# output-stream#]
              output-stream#))

         ~(method* 'encode-value-stream
            `([this# selector# encoder-stream#]
              encoder-stream#))

         ~(method* 'decode-byte-stream
            `([this# header# input-stream#]
              input-stream#))

         ~(method* 'decode-value-stream
            `([this# header# decoder-stream#]
              decoder-stream#))

         ~@(vals (dissoc codec-methods
                         'processable?
                         'select-header
                         'encode-byte-stream
                         'encode-value-stream
                         'decode-byte-stream
                         'decode-value-stream))

         ~@more)

       (alter-meta! (var ~(symbol (str "->" name-sym))) assoc :private true)
       (alter-meta! (var ~(symbol (str "map->" name-sym))) assoc :private true))))
