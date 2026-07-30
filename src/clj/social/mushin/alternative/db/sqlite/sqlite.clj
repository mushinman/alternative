(ns social.mushin.alternative.db.sqlite.sqlite
  (:require [clj-uuid :as uuid])
  (:import [social.mushin.alternative.hosted.db.sqlite SQLiteFunction]
           [java.sql Connection]
           [clojure.lang IFn]
           [org.sqlite Function]))


(defn argc
  ^Integer
  [^SQLiteFunction func]
  (.argumentCount func))

(defn throw-error
  [^SQLiteFunction func ^String err-msg]
  (.throwError func err-msg))


(defn return-void
  [^SQLiteFunction func]
  (.returnVoid func))

(defn return-bytes
  [^SQLiteFunction func ^bytes bytes]
  (.returnBytes func bytes))

(defn return-double
  [^SQLiteFunction func ^double double]
  (.returnDouble func double))

(defn return-int
  [^SQLiteFunction func ^Integer int]
  (.returnInt func int))

(defn return-long
  [^SQLiteFunction func ^long long]
  (.returnLong func long))

(defn return-string
  [^SQLiteFunction func ^String string]
  (.returnString func string))

(defn arg-double
  ^double
  [^SQLiteFunction func ^Integer n] 
  (.getNthArgAsDouble func n))

(defn arg-int
  ^Integer
  [^SQLiteFunction func ^Integer n] 
  (.getNthArgAsInt func n))

(defn arg-long
  ^long
  [^SQLiteFunction func ^Integer n] 
  (.getNthArgAsLong func n))

(defn arg-string
  ^String
  [^SQLiteFunction func ^Integer n] 
  (.getNthArgAsString func n))

(defn arg-type
  ^Integer
  [^SQLiteFunction func ^Integer n] 
  (.getNthArgType func n))

(def default-functions
  {"uuidv7-text" (fn [f]
                   (return-string f (str (uuid/v7))))

   "uuidv7-blob" (fn [f]
                   (return-bytes f (uuid/to-byte-array (uuid/v7))))})

(defn add-functions-to-con
  ^Connection
  ([^Connection con]
   (add-functions-to-con con)
   con)
  ([^Connection con functions]
   (doseq [[^String name ^IFn func] functions]
     (Function/create con name (SQLiteFunction. func)))
   con))
