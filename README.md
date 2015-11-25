clj-multicodec
==============

[![Build Status](https://travis-ci.org/greglook/clj-multicodec.svg?branch=develop)](https://travis-ci.org/greglook/clj-multicodec)
[![Coverage Status](https://coveralls.io/repos/greglook/clj-multicodec/badge.svg?branch=develop&service=github)](https://coveralls.io/github/greglook/clj-multicodec?branch=develop)
[![API codox](https://img.shields.io/badge/doc-API-blue.svg)](https://greglook.github.io/clj-multicodec/api/)
[![marginalia docs](http://img.shields.io/badge/doc-marginalia-blue.svg)](https://greglook.github.io/clj-multicodec/marginalia/uberdoc.html)
[![Join the chat at https://gitter.im/greglook/clj-multicodec](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/greglook/clj-multicodec)

A Clojure library implementing the
[multicodec](https://github.com/jbenet/multicodec) standard. This provides a
content-agnostic way to prefix binary data with its encoding in a way that is
both human and machine-readable.

## Installation

Library releases are published on Clojars. To use the latest version with
Leiningen, add the following dependency to your project definition:

[![Clojars Project](http://clojars.org/mvxcvi/multicodec/latest-version.svg)](http://clojars.org/mvxcvi/multicodec)

## Usage

The library specifies two main protocols for codecs: `Encoder` and the
accompanying `encode!`, and `Decoder` and `decode!`. These operate directly on
output and input streams, respectively. The functions `encode` and `decode` (no
bangs) write and read byte arrays instead of streams.

There are a few simple codecs included as utilities, for example the text codec:

```clojure
=> (require '[multicodec.core :as codec]
            '[multicodec.codecs :as codecs])

; The text codec converts between characters and bytes using a charset:
=> (def text (codecs/text-codec))

; The default is UTF-8, and sets the codec's header:
=> text
#multicodec.codecs.TextCodec
{:charset #<sun.nio.cs.UTF_8@11207688 UTF-8>,
 :header "/text/UTF-8"}

; Text encoding turns strings into bytes:
=> (def encoded (codec/encode text "abc 123!"))

=> (seq encoded)
(97 98 99 32 49 50 51 33)

=> (map char encoded)
(\a \b \c \space \1 \2 \3 \!)

; Decoding reads bytes into a string:
=> (codec/decode text encoded)
"abc 123!"
```

More sophisticated usage involves reading and writing headers to determine what
the content is:

```clojure
; Multiplexing codecs choose among multiple children:
=> (def mux (codecs/mux-codec
              :text (codecs/text-codec)
              :bin  (codecs/bin-codec)))

; By default, the first codec is used for encoding:
=> (def encoded (codec/encode mux "abc 123!"))

; The initial 12 is the length of the codec header:
=> (seq encoded)
(12 47 116 101 120 116 47 85 84 70 45 56 10 97 98 99 32 49 50 51 33)

; Here you can see the header prefixing the encoded value:
=> (map char encoded)
(\formfeed \/ \t \e \x \t \/ \U \T \F \- \8 \newline \a \b \c \space \1 \2 \3 \!)

; Decoding reads the header and dispatches to the text codec:
=> (codec/decode mux encoded)
"abc 123!"

; Lets create a binary-encoded value by selecting the bin codec:
=> (def encoded (codec/encode (codecs/mux-select mux :bin)
                              (.getBytes "foo")))

=> (seq encoded)
(6 47 98 105 110 47 10 102 111 111)

=> (map char encoded)
(\ \/ \b \i \n \/ \newline \f \o \o)

; Decoding with the multiplexer reads the header and selects the binary codec:
=> (String. (codec/decode mux encoded))
"foo"
```

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
