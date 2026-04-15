(ns social.mushin.alternative.errors
  (:import [social.mushin.alternative.hosted.application AlternativeApplicationException]
           [social.mushin.alternative.hosted.db AlternativeDatabaseException]
           [social.mushin.alternative.hosted.application.error AlternativeException]))


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
  (.-code e))

