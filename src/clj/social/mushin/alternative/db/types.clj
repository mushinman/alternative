(ns social.mushin.alternative.db.types
  "A collection of defined malli schema, validation functions, and definitions
  for database data and types."
  (:require [malli.experimental.time :as mallt]
            [social.mushin.alternative.validators :refer [is-email-valid?]]))

(def created-at
  "Malli schema for a created-at timestamp."
  [:created-at (mallt/-zoned-date-time-schema)])

(def updated-at
  "Malli schema for an updated-at timestamp."
  [:updated-at (mallt/-zoned-date-time-schema)])

(def uri-schema
  "Malli schema for URIs."
  [:fn {:error/message "Must be a URI"} uri?])

(def email-schema
  "Malli schmea for email addressess."
  [:fn {:error/message "Must be valid email address"}
   (fn [v]
     (and (string? v)
          (is-email-valid? v)))])
