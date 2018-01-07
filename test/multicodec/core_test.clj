(ns multicodec.core-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [multicodec.codecs.bin :refer [bin-codec]]
    [multicodec.codecs.compress :refer [gzip-codec]]
    [multicodec.codecs.text :refer [text-codec]]
    [multicodec.codecs.label :refer [label-codec]]
    [multicodec.core :as codec])
  (:import
    (java.io
      ByteArrayInputStream
      ByteArrayOutputStream)))


(deftest standard-headers
  (doseq [[codec-key path] codec/headers]
    (is (keyword? codec-key)
        "codec names should be keywords")
    (is (string? path)
        "codec paths should be strings")
    (is (str/starts-with? path "/")
        "codec paths should start with a slash")))


(deftest mux-codec-selection
  (let [factory (codec/mux
                  :bin (bin-codec)
                  :foo (label-codec "/foo/")
                  :text (text-codec)
                  :gzip (gzip-codec))]
    (testing "bad encoder args"
      (is (thrown? Exception
            (codec/encoder-stream factory (ByteArrayOutputStream.) [])))
      (is (thrown-with-msg? Exception #"No codec found for selector :not-found"
            (codec/encoder-stream factory (ByteArrayOutputStream.) [:not-found])))
      (is (thrown-with-msg? Exception #"Encoder selection did not result in an encoder stream!"
            (codec/encoder-stream factory (ByteArrayOutputStream.) [:gzip]))))
    (testing "direct codec selection"
      (let [baos (ByteArrayOutputStream.)]
        (with-open [encoder (codec/encoder-stream factory baos [:text])]
          (is (= 16 (codec/write! encoder "hello multicodec")))
          (is (= 14 (codec/write! encoder ", how are you?"))))
        (let [output-bytes (.toByteArray baos)
              bais (ByteArrayInputStream. output-bytes)]
          (is (= 43 (count output-bytes)))
          (with-open [decoder (codec/decoder-stream factory bais)]
            (is (= "hello multicodec, how are you?"
                   (codec/read! decoder)))))))))
