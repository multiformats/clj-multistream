(ns multicodec.codec.label
  "Wrapper codec which writes out and reads a header before the value body.
  This doesn't change any of the following data, but is useful for labeling the
  purpose of the data."
  (:require
    [multicodec.core :as codec :refer [defcodec]]))


(defcodec LabelCodec
  [header]

  (processable?
    [this hdr]
    (= hdr header))


  (encode-stream
    [this selector stream]
    (codec/write-header! stream header)
    stream)


  (decode-stream
    [this header stream]
    stream))


(defn label-codec
  "Creates a new codec which will write the given header before passing on the
  stream for further encoding."
  [header]
  (->LabelCodec header))
