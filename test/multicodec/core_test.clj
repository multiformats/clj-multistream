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
