(ns multicodec.header
  "Functions for handling multicodec header paths.

  On error, these functions throw an `ExceptionInfo` with ex-data containing
  `:type :multicodec/bad-header` to indicate the problem. The data map will also
  usually have `:header` and `:length` entries."
  (:require
    [clojure.string :as str])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream
      InputStream
      OutputStream)
    java.nio.charset.Charset))


(def ^:dynamic *headers*
  "This var can be bound in a thread to discover what headers were actually
  read or written during some codec operations. Each time a header is
  successfully read or written, it will be `conj`ed into the collection in this
  var."
  nil)


(def ^:no-doc ^:const max-header-length
  "The maximum length (in bytes) a header path can be."
  127)


(def ^:no-doc ^java.nio.charset.Charset header-charset
  "The character set that codec headers are encoded with."
  (Charset/forName "UTF-8"))


(defn- bad-header-ex
  "Creates an exception for a bad header value. The ex-data map will have
  `:type :multicodec/bad-header` and any additional key-value pairs passed to
  the function."
  [message & {:as info}]
  (ex-info message (assoc info :type :multicodec/bad-header)))



;; ## Header Encoding

(defn encode-header
  "Returns the byte-encoding of the header path. The path is trimmed and has a
  newline appended to it before encoding.

  Throws a bad-header exception if the path is too long."
  ^bytes
  [path]
  (let [header (str (str/trim path) "\n")
        header-bytes (.getBytes header header-charset)
        length (count header-bytes)]
    (when (> length max-header-length)
      (throw (bad-header-ex
               (format "Header paths longer than %d bytes are not supported: %d"
                       max-header-length length)
               :header header
               :length length)))
    (let [encoded (byte-array (inc length))]
      (aset-byte encoded 0 (byte length))
      (System/arraycopy header-bytes 0 encoded 1 length)
      encoded)))


(defn write-header!
  "Writes a multicodec header for `path` to the given stream. Returns the number
  of bytes written."
  [^OutputStream output path]
  (let [header (encode-header path)]
    (.write output header)
    (when (thread-bound? #'*headers*)
      (set! *headers* (conj *headers* path)))
    (count header)))



;; ## Header Decoding

(defn- take-bytes!
  "Attempts to read `length` bytes from the given stream. Returns a byte array with
  the read bytes."
  ^bytes
  [^InputStream input length]
  (let [content (byte-array length)]
    (loop [offset 0
           remaining length]
      (let [n (.read input content offset remaining)]
        (if (< n remaining)
          (recur (+ offset n) (- remaining n))
          content)))))


(defn read-header!
  "Attempts to read a multicodec header from the given stream. Returns the
  header path.

  Throws a bad-header exception if the stream does not have a valid header."
  ^String
  [^InputStream input]
  (let [length (.read input)]
    (when-not (< length 128)
      (throw (bad-header-ex
               (format "First byte in stream is not a valid header length: %02x"
                       length)
               :length length)))
    (let [header (String. (take-bytes! input length) header-charset)]
      (when-not (.endsWith header "\n")
        (throw (bad-header-ex
                 (str "Last byte in header is not a newline: "
                      (pr-str (.charAt header (dec (count header)))))
                 :header header
                 :length length)))
      (let [path (str/trim-newline header)]
        (when (thread-bound? #'*headers*)
          (set! *headers* (conj *headers* path)))
        path))))
