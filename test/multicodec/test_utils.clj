(ns multicodec.test-utils
  (:require
    [multicodec.core :as codec]))


(defn mock-codec
  [tag header]
  (reify

    clojure.lang.ILookup

    (valAt [_ k]
      (when (= :header k) header))

    codec/Encoder

    (encodable? [_ value]
      (get (meta value) tag true))

    (encode! [_ output value]
      (let [content (.getBytes (pr-str value))]
        (.write output content)
        (count content)))

    codec/Decoder

    (decodable? [_ header']
      (= header header'))

    (decode! [_ input]
      [tag (slurp input)])))
