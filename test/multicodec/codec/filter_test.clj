(ns multicodec.codec.filter-test
  (:require
    [clojure.test :refer :all]
    [multicodec.codec.filter :as filter]
    [multicodec.core :as codec]))


(deftest filter-codec
  (testing "empty codec"
    (let [codec (filter/filter-codec)]
      (is (not (codec/processable? codec "/bin/")))
      (is (not (codec/processable? codec "/filter/")))
      (is (= :stream (codec/encode-stream codec nil :stream))
          "lack of encode-fn returns original stream")
      (is (= :stream (codec/decode-stream codec nil :stream))
          "lack of decode-fn returns original stream")))
  (testing "filtered codec"
    (let [codec (filter/filter-codec
                  :encode-fn inc
                  :decode-fn dec)]
      (with-open [stream (codec/encode-stream
                           codec nil
                           (reify codec/EncoderStream
                             (write! [_ value]
                               (is (= 9 value))
                               1)
                             java.io.Closeable
                             (close [_] nil)))]
        (is (= 1 (codec/write! stream 8))))
      (with-open [stream (codec/decode-stream
                           codec nil
                           (reify codec/DecoderStream
                             (read! [_] 1)
                             java.io.Closeable
                             (close [_] nil)))]
        (is (= 0 (codec/read! stream)))))))
