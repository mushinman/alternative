(ns social.mushin.alternative.db.types
  "A collection of defined malli schema, validation functions, and definitions
  for database data and types."
  (:require [lambdaisland.uri :as li-uri]
            [malli.experimental.time :as mallt]
            [social.mushin.alternative.validators :refer [is-email-user-valid? is-email-valid?]]
            [social.mushin.alternative.utils :refer [grapheme-count]]))

(def created-at
  "Malli schema for a created-at timestamp."
  [:created-at (mallt/-zoned-date-time-schema)])

(def updated-at
  "Malli schema for an updated-at timestamp."
  [:updated-at (mallt/-zoned-date-time-schema)])
            
(defn is-valid-nickname?
  "Return true if `v` is a valid nickname, otherwise false."
  [v]
  (and (string? v)
       (<= 1 (grapheme-count v) 32)
       (is-email-user-valid? v)))

(def uri-schema
  "Malli schema for lambdaisland URIs."
  [:fn {:error/message "Must be a URI"} 
   (fn [v]
     (or (li-uri/uri? v)
         (uri? v)))])

(def nickname-schema
  "Malli schmea for nicknames."
  [:fn {:error/message "Must be valid email username, not empty, and under 32 characters"} is-valid-nickname?])

(def email-schema
  "Malli schmea for email addressess."
  [:fn {:error/message "Must be valid email address"}
   (fn [v]
     (and (string? v)
          (is-email-valid? v)))])
