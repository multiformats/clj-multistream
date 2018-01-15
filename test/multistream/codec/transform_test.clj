(ns multistream.codec.transform-test
  (:require
    [clojure.test :refer :all]
    [multistream.codec :as codec]
    [multistream.codec.transform :as transform]))


(deftest transform-codec
  (testing "empty codec"
    (let [codec (transform/transform-codec "/foo/")]
      (is (not (codec/processable? codec "/bin/")))
      (is (not (codec/processable? codec "/transform/")))
      (is (= :stream (codec/encode-value-stream codec nil :stream))
          "lack of encode-fn returns original stream")
      (is (= :stream (codec/decode-value-stream codec nil :stream))
          "lack of decode-fn returns original stream")))
  (testing "transformed codec"
    (let [codec (transform/transform-codec
                  "/foo/"
                  :encode-fn inc
                  :decode-fn dec)]
      (is (not (codec/processable? codec "/bin/")))
      (is (codec/processable? codec "/foo/"))
      (with-open [stream (codec/encode-value-stream
                           codec nil
                           (reify codec/EncoderStream
                             (write! [_ value]
                               (is (= 9 value))
                               1)
                             java.io.Closeable
                             (close [_] nil)))]
        (is (= 1 (codec/write! stream 8))))
      (with-open [stream (codec/decode-value-stream
                           codec nil
                           (reify codec/DecoderStream
                             (read! [_] 1)
                             java.io.Closeable
                             (close [_] nil)))]
        (is (= 0 (codec/read! stream)))))))
