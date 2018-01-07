(ns multicodec.core-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [multicodec.codecs.bin :refer [bin-codec]]
    [multicodec.codecs.compress :refer [gzip-codec]]
    [multicodec.codecs.text :refer [text-codec]]
    [multicodec.codecs.label :refer [label-codec]]
    [multicodec.core :as codec]
    [multicodec.header :as header])
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
            (codec/encoder-stream factory (ByteArrayOutputStream.) [:gzip])))
      (is (thrown-with-msg? Exception #"The output argument to encoder-stream must be a java\.io\.OutputStream"
            (codec/encoder-stream factory "foo" [])))
      (is (thrown-with-msg? Exception #"The input argument to decoder-stream must be a java\.io\.InputStream"
            (codec/decoder-stream factory "foo"))))
    (testing "direct codec selection"
      (let [baos (ByteArrayOutputStream.)]
        (with-open [encoder (codec/encoder-stream factory baos [:text])]
          (is (= 16 (codec/write! encoder "hello multicodec")))
          (is (= 14 (codec/write! encoder ", how are you?"))))
        (let [output-bytes (.toByteArray baos)]
          (is (= 43 (count output-bytes)))
          (with-open [decoder (codec/decoder-stream factory (ByteArrayInputStream. output-bytes))]
            (is (= "hello multicodec, how are you?"
                   (codec/read! decoder)))))))
    (testing "header codec selection"
      (let [baos (ByteArrayOutputStream.)]
        (with-open [encoder (codec/encoder-stream factory baos ["/foo/"
                                                                "/gzip/"
                                                                "/text/UTF-8"])]
          (is (= 16 (codec/write! encoder "hello multicodec")))
          (is (= 14 (codec/write! encoder ", how are you?"))))
        (let [output-bytes (.toByteArray baos)]
          (is (= 78 (count output-bytes)))
          (binding [header/*headers* []]
            (with-open [decoder (codec/decoder-stream factory (ByteArrayInputStream. output-bytes))]
              (is (= "hello multicodec, how are you?"
                     (codec/read! decoder))))
            (= ["/foo/" "/gzip/" "/text/UTF-8"] header/*headers*)))))))
