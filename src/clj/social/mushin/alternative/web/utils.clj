(ns social.mushin.alternative.web.utils)

(def id-schema
  "A schema for a map with just a key called `:id` and a `:uuid` value.
  Useful for path params."
  [:map [:id :uuid]])
