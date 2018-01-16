clj-multistream
===============

[![CircleCI](https://circleci.com/gh/multiformats/clj-multistream/tree/develop.svg?style=shield&circle-token=42759f9031db82a53ecf03327cd673bc57043e62)](https://circleci.com/gh/multiformats/clj-multistream/tree/develop)
[![codecov](https://codecov.io/gh/multiformats/clj-multistream/branch/develop/graph/badge.svg)](https://codecov.io/gh/multiformats/clj-multistream)
[![API codox](https://img.shields.io/badge/doc-API-blue.svg)](https://multiformats.github.io/clj-multistream/api/)
[![marginalia docs](https://img.shields.io/badge/doc-marginalia-blue.svg)](https://multiformats.github.io/clj-multistream/marginalia/uberdoc.html)
[![](https://img.shields.io/badge/freenode-%23ipfs-blue.svg?style=flat-square)](https://webchat.freenode.net/?channels=%23ipfs)
[![](https://img.shields.io/badge/project-multiformats-blue.svg?style=flat-square)](https://github.com/multiformats/multiformats)
[![](https://img.shields.io/badge/readme%20style-standard-brightgreen.svg?style=flat-square)](https://github.com/RichardLitt/standard-readme)

> Clojure implementation of multistream codecs

A Clojure library implementing the
[multistream](https://github.com/multiformats/multistream) standard. This
provides a content-agnostic way to prefix binary data with its encoding in a way
that is both human and machine-readable.

## Table of Contents

- [Install](#install)
- [Usage](#usage)
- [Maintainers](#maintainers)
- [Contribute](#contribute)
- [License](#license)

## Install

Library releases are published on Clojars. To use the latest version with
Leiningen, add the following dependency to your project definition:

[![Clojars Project](https://clojars.org/mvxcvi/multistream/latest-version.svg)](https://clojars.org/mvxcvi/multistream)

## Usage

The `multistream.codec` namespace contains the main library API, with codecs
generally defined in `multistream.codec.*` namespaces. The library models codecs
generically using three main protocols:

- An `EncoderStream` represents an open output which values can be written to
  using `codec/write!`.
- A `DecoderStream` is the other end, a stream of values consumed  by calling
  `codec/read!`.
- The `Codec` protocol specifies several methods which define how to select and
  compose the codec with others to produce streams.

To demonstrate, let's see a simple direct codec usage:

```clojure
=> (require
     '[multistream.codec :as codec]
     '[multistream.codec.text :refer [text-codec])

; The text codec converts between characters and bytes using a charset:
=> (def text (text-codec))

=> text
#multistream.codec.text.TextCodec
{:default-charset #<sun.nio.cs.UTF_8@11207688 UTF-8>,
 :buffer-size 512}

; What header would this codec use by default?
=> (codec/select-header text nil)
"/text/UTF-8"

; We can test what sort of headers a codec can handle:
=> (codec/processable? text "/foo")
false

=> (codec/processable? text "/text/UTF-8")
true

; The text codec can even handle other charsets:
=> (codec/processable? text "/text/US-ASCII")
true

; Text encoding turns strings into bytes:
=> (def encoded (codec/encode text "abc 123!"))

=> (map char encoded)
(\formfeed \/ \t \e \x \t \/ \U \T\ F\- \8 \newline \a \b \c \space \1 \2 \3 \!)

; Decoding reads bytes into a string:
=> (codec/decode text encoded)
"abc 123!"
```

The `encode` and `decode` functions provide a simple way to use a codec
directly, returning and consuming byte arrays respectively. As seen above, the
encoded form will include the codec header and verify it on decode.

### Codec Construction

The `CodecFactory` protocol provides a constructor for encoder and decoder
streams, using some saved configuration. The easiest way to use these together
is with a multicodec factory. Let's introduce some more codecs to make the
example interesting:

```clojure
=> (require
     '[multistream.codec.transform :refer [transform-codec]]
     '[multistream.codec.compress :refer [gzip-codec]])

=> (def multicodec
     (codec/multi
       :gzip (gzip-codec)
       :text (text-codec)
       :xform (transform-codec "/foo"
                :decode-fn clojure.string/upper-case)))
```

The `transform-codec` is wrapper which provides hooks for running some
transformation functions before encoding and after decoding, associated with a
header. The `gzip-codec` will wrap compression encoding around all bytes after
it. Finally, we've grouped the codecs into a single multicodec factory. Note
that the codecs don't have any explicit dependencies on each other; they compose
generically.

To construct new encoder streams, we must now provide a sequence of _selectors_
to choose which codecs to invoke:

```clojure
=> (def encoded
     (let [baos (java.io.ByteArrayOutputStream.)]
       (with-open [encoder (codec/encoder-stream
                             multicodec
                             [:xform :gzip :text]
                             baos)]
         (codec/write! encoder "hello multistream")
         (codec/write! encoder ", how are you?"))
       (.toByteArray baos)))

=> (count encoded)
78

; Note the headers are composed in the output - we can't take more characters
; here because the rest of the data is compressed, including the text header.
=> (map char (take 14 encoded))
(\ \/ \f \o \o \newline \ \/ \g \z \i \p \/ \newline)
```

To read the data back, we can construct a decoder stream using the same factory.
This time however, there are no selectors, since the headers are read from the
input stream directly to choose codecs.

```clojure
=> (with-open [decoder (codec/decoder-stream
                         multicodec
                         (java.io.ByteArrayInputStream. encoded))]
     (prn (::codec/headers decoder))
     (codec/read! decoder))
; ("/foo" "/gzip/" "/text/UTF-8")
"HELLO MULTISTREAM, HOW ARE YOU?"
```

Let's unpack what happened in the above example:

- In order to construct the decoder stream, the factory read the first header
  from the input stream. It is `/foo`, and the factory matches this against the
  `:xform` codec, which is used to wrap the input stream.
- In this case, the `transform-codec` doesn't modify the input stream, so the
  factory reads the next header from the stream. It is `/gzip/`, and matches the
  `:gzip` codec, which wraps the input stream in a `GZIPInputStream` to
  decompress it.
- The result is still an `InputStream`, so the factory reads the next header
  again. This time it is `/text/UTF-8`, so the `:text` codec wraps the input and
  returns a `TextDecoderStream`.
- Now the factory takes the decoder stream and wraps it back up the codecs in
  reverse order to return the final stream to the user.
- The decoder stream has the read codecs available under the
  `:multistream.codec/headers` key.
- Finally, the result shows that the decoding function in the transform codec is
  upcasing everything read back from the encoded data.

### Codec Composition

When the multicodec factory composes codecs together to create streams, the
wrapping happens in two stages. First, the _byte stream_ is passed through each
codec in the order given, and may elect to wrap the stream before passing it to
the next codec. The final codec must be a format codec, which creates a _value
stream_ from a raw byte stream. The value stream is then wrapped by each codec
in reverse order, and the user receives the final stream.

![stream construction](doc/stream-construction.jpg)

The above example illustrates the code examples above. The `xform` and `gzip`
codecs are wrappers around the `text` format. The result will be a composite
stream which writes out gzip-compressed UTF-8 text, which will always be read
back in all upper-case.

### Implementing Codecs

The `multistream.codec` namespace includes some utility macros for defining new
codecs and streams:

- `defencoder` defines an `EncoderStream` record. The first method should be
  `write!` and any other protocol/interface implementations may follow. This
  record implements `java.io.Closeable` automatically and will attempt to close
  the first record attribute defined, so make sure this is the wrapped stream.
- `defdecoder` defines a `DecoderStream` record. The first method should be
  `read!` and any other protocol/interface implementations may follow. Similar
  to `defencoder`, this implements `Closeable` on the first attribute.
  Additionally, the resulting streams implement `IReduceInit` to repeatedly read
  and operate on values from the stream.
- `defcodec` defines a `Codec` record with default no-op implementations for
  every protocol method. This allows for implementing just the methods you need
  to override in the codec.

As a simple example, this is the entire gzip codec implementation:

```clojure
(defcodec GZIPCodec
  [header]

  (encode-byte-stream
    [this selector output-stream]
    (GZIPOutputStream. ^OutputStream output-stream))

  (decode-byte-stream
    [this header input-stream]
    (GZIPInputStream. ^InputStream input-stream)))
```

The other [included codecs](src/multistream/codec/) should provide some useful
examples as well.

## Maintainers

Captain: [@greglook](https://github.com/greglook).

## Contribute

Contributions welcome. Please check out [the issues](https://github.com/multiformats/clj-multistream/issues).

Check out our [contributing document](https://github.com/multiformats/multiformats/blob/master/contributing.md) for more information on how we work, and about contributing in general. Please be aware that all interactions related to multiformats are subject to the IPFS [Code of Conduct](https://github.com/ipfs/community/blob/master/code-of-conduct.md).

Small note: If editing the README, please conform to the [standard-readme](https://github.com/RichardLitt/standard-readme) specification.

## License

This is free and unencumbered software released into the public domain.
See the [UNLICENSE](UNLICENSE) file for more information.
