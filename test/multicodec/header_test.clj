(ns multicodec.header-test
  (:require
    [clojure.test :refer :all]
    [multicodec.header :as mh])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


;; ## Helper Functions

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


(defmacro is-ex-data-thrown?
  [expected & body]
  `(try
     ~@body
     (is false "should have thrown exception info")
     (catch RuntimeException ~'ex
       (testing "thrown exception"
         (is (ex-data ~'ex) "should have ex-data")
         ~@(map
             (fn [[k v]]
               `(is (~'= ~v (~'get (~'ex-data ~'ex) ~k))
                    ~(str "should have " k " " v " in ex-data")))
             expected))
       true)))



;; ## Header Tests

(deftest header-encoding
  (testing "127 byte header"
    (let [path (apply str "/" (repeat 125 "x"))]
      (is (= 128 (count (mh/encode-header path))))))
  (testing "128 byte header"
    (let [path (apply str "/" (repeat 126 "x"))]
      (is-ex-data-thrown?
        {:type :multicodec/bad-header}
        (mh/encode-header path))))
  (testing "path examples"
    (are [hex path] (is (bytes= (hex-bytes hex) (mh/encode-header path)))
      "052f62696e0a" "/bin"    ; raw binary
      "042f62320a"   "/b2"     ; ascii base2  (binary)
      "052f6231360a" "/b16"    ; ascii base16 (hex)
      "052f6233320a" "/b32"    ; ascii base32
      "052f6235380a" "/b58"    ; ascii base58
      "052f6236340a" "/b64"))) ; ascii base64



(deftest bad-headers
  (testing "bad header length"
    (let [example (byte-array 10)
          bais (ByteArrayInputStream. example)]
      (aset-byte example 0 -128)
      (is-ex-data-thrown?
        {:type :multicodec/bad-header}
        (mh/read-header! bais))))
  (testing "missing newline"
    (let [example (byte-array 10)
          bais (ByteArrayInputStream. example)]
      (aset-byte example 0 5)
      (is-ex-data-thrown?
        {:type :multicodec/bad-header}
        (mh/read-header! bais)))))


(defn test-stream-roundtrip
  [^String path ^String content]
  (let [baos (ByteArrayOutputStream.)]
    (testing "write-header!"
      (is (= (+ 2 (count (.getBytes path mh/header-charset)))
             (mh/write-header! baos path))
          "should write correct number of bytes to stream"))
    (.write baos (.getBytes content))
    (let [content-bytes (.toByteArray baos)
          bais (ByteArrayInputStream. content-bytes)]
      (testing "read-header!"
        (is (= path (mh/read-header! bais))
            "should read correct path from stream")
        (is (= content (slurp bais))
            "should leave stream correctly positioned")))))


(deftest stream-roundtrips
  (test-stream-roundtrip "/b16" "0123456789abcdef"))


(deftest header-collecting-var
  (let [baos (ByteArrayOutputStream.)]
    (testing "writing headers"
      (binding [mh/*headers* []]
        (mh/write-header! baos "/foo/v1")
        (mh/write-header! baos "/bar/v3")
        (is (= ["/foo/v1" "/bar/v3"] mh/*headers*)
            "should add to *headers*")))
    (testing "reading headers"
      (binding [mh/*headers* []]
        (let [bais (ByteArrayInputStream. (.toByteArray baos))]
          (is (= "/foo/v1" (mh/read-header! bais)))
          (is (= "/bar/v3" (mh/read-header! bais))))
        (is (= ["/foo/v1" "/bar/v3"] mh/*headers*)
            "should add to *headers*")))))
