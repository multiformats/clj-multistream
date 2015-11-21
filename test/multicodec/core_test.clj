(ns multicodec.core_test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [multicodec.core :as multicodec])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream
      IOException)))


(defn hex-bytes
  "Converts a hex string into a byte array."
  ^bytes
  [^String value]
  (when value
    (if (empty? value)
      (byte-array 0)
      (.toByteArray (BigInteger. value 16)))))


(defn bytes=
  "Returns true if two byte sequences are the same length and have the same
  byte content."
  [a b]
  (and (= (count a) (count b))
       (every? true? (map = a b))))


(deftest header-encoding
  (is (= 128 (count (multicodec/encode-header (apply str (repeat 126 "x")))))
      "should support up to 127 byte header")
  (is (thrown? IllegalArgumentException
               (multicodec/encode-header (apply str (repeat 127 "x"))))
      "should throw exception on headers longer than 127 bytes")
  (are [hex path] (is (bytes= (hex-bytes hex) (multicodec/encode-header path)))
    "052f62696e0a" "/bin"   ; raw binary
    "042f62320a"   "/b2"    ; ascii base2  (binary)
    "052f6231360a" "/b16"   ; ascii base16 (hex)
    "052f6233320a" "/b32"   ; ascii base32
    "052f6235380a" "/b58"   ; ascii base58
    "052f6236340a" "/b64")) ; ascii base64


(defn test-stream-roundtrip
  [^String path ^String content]
  (let [baos (ByteArrayOutputStream.)]
    (testing "write-header!"
      (is (= (+ 2 (count (.getBytes path multicodec/header-charset)))
             (multicodec/write-header! baos path))
          "should write correct number of bytes to stream"))
    (.write baos (.getBytes content))
    (let [content-bytes (.toByteArray baos)
          bais (ByteArrayInputStream. content-bytes)]
      (testing "read-header!"
        (is (= path (multicodec/read-header! bais))
            "should read correct path from stream")
        (is (= content (slurp bais))
            "should leave stream correctly positioned")))))


(deftest stream-headers
  (testing "bad header length"
    (let [example (byte-array 10)
          bais (ByteArrayInputStream. example)]
      (aset-byte example 0 -128)
      (is (thrown-with-msg? IOException #"valid header length"
            (multicodec/read-header! bais)))))
  (testing "missing newline"
    (let [example (byte-array 10)
          bais (ByteArrayInputStream. example)]
      (aset-byte example 0 5)
      (is (thrown-with-msg? IOException #"a newline"
            (multicodec/read-header! bais)))))
  (test-stream-roundtrip "/b16" "0123456789abcdef"))


(defn mock-codec
  [tag header]
  (reify
    clojure.lang.ILookup
    (valAt [_ k]
      (when (= :header k) header))
    multicodec/Encoder
    (encode [_ output value]
      (let [content (.getBytes (pr-str value))]
        (.write output content)
        (count content)))
    multicodec/Decoder
    (decode [_ input]
      [tag (slurp input)])))


(deftest multiplex-codec
  (testing "codec construction"
    (is (thrown? IllegalArgumentException (multicodec/mux-codec))
        "construction with no codecs should throw exception")
    (is (thrown? IllegalArgumentException (multicodec/mux-codec (mock-codec :foo nil)))
        "construction with no-header arg should throw exception"))
  (testing "encoding with no selected codec"
    (let [mux (assoc (multicodec/mux-codec (mock-codec :foo "/bar"))
                     :select-encoder (constantly nil))]
      (is (thrown? IllegalStateException (multicodec/encode mux nil nil)))))
  (let [mux (multicodec/mux-codec
              (mock-codec :foo "/foo")
              (mock-codec :bar "/bar"))]
    (testing "encoding roundtrip"
      (let [baos (ByteArrayOutputStream.)
            out-len (multicodec/encode mux baos 1234)
            encoded (.toByteArray baos)]
        (is (= 10 out-len) "should write the correct number of bytes")
        (is (= "/foo" (multicodec/read-header! (ByteArrayInputStream. encoded))))
        (is (= [:foo "1234"] (multicodec/decode mux (ByteArrayInputStream. encoded))))))
    (testing "no-matching decoder"
      (let [baos (ByteArrayOutputStream.)]
        (multicodec/write-header! baos "/baz/qux")
        (.write baos (.getBytes "abcd"))
        (is (thrown? IllegalStateException
                     (multicodec/decode mux (ByteArrayInputStream. (.toByteArray baos)))))))))
