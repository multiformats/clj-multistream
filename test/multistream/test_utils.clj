(ns multistream.test-utils
  (:require
    [clojure.test :as test]
    [multistream.codec :as codec]))


(defmethod test/assert-expr 'err-thrown?
  [msg [_ err-type & body]]
  `(try
     ~@body
     (test/do-report
       {:type :fail
        :message ~(str "Expected error type " err-type " from body")
        :expected ~err-type
        :actual nil})
     (catch Exception ex#
       (let [actual-type# (:type (ex-data ex#))]
         (if (= ~err-type actual-type#)
           (test/do-report
             {:type :pass
              :message ~msg
              :expected ~err-type
              :actual actual-type#})
           (test/do-report
             {:type :fail
              :message ~msg
              :expected ~err-type
              :actual actual-type#}))))))
