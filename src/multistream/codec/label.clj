(ns multistream.codec.label
  "Wrapper codec which writes out and reads a header before the value body.
  This doesn't change any of the following data, but is useful for labeling the
  purpose of the data."
  (:require
    [multistream.codec :as codec :refer [defcodec]]))


; TODO: obviated by filter-codec?
(defcodec LabelCodec
  [header]

  (encode-byte-stream
    [this selector output-stream]
    (codec/write-header! output-stream header)
    output-stream))


(defn label-codec
  "Creates a new codec which will write the given header before passing on the
  stream for further encoding."
  [header]
  (->LabelCodec header))
