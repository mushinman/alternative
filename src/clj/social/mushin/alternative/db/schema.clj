(ns social.mushin.alternative.db.schema
  (:import [java.time ZonedDateTime]))


(defmulti doc-schema-check first)

(defmethod doc-schema-check :default
  [[_ op docs]]
  docs)

(defn is-zdt?
  [d]
  (instance? ZonedDateTime d))

