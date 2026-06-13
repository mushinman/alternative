(ns social.mushin.alternative.errors
  (:import [social.mushin.alternative.hosted.application AlternativeApplicationException]
           [social.mushin.alternative.hosted.db AlternativeDatabaseException AlternativeCacheMissException]
           [social.mushin.alternative.hosted.application.error AlternativeException]))

(defn cache-miss-ex
  "Create a cache miss exception. This is useful when a lookup function for the cache
   could not return anything useful, which prevents it from being stored in the cache."
  [key]
  (AlternativeCacheMissException. (str "Cache miss for " key) :cache-miss {}))

(defn db-error
  ([^String msg code ctx ^Throwable cause]
   (AlternativeDatabaseException. msg code ctx cause))
  ([^String msg code ctx]
   (db-error msg code ctx nil)))

(defn app-error
  ([^String msg code ctx ^Throwable cause]
   (AlternativeApplicationException. msg code ctx cause))
  ([^String msg code ctx]
   (app-error msg code ctx nil)))

(defn ex-code
  [^AlternativeException e]
  (.getCode e))

