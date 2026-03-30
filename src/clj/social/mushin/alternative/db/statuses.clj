(ns social.mushin.alternative.db.statuses
  (:require [social.mushin.alternative.db.types :as types]))


(def status-types-schema
  [:enum :text :image :animated-image :video :comic :microblog :meme :tombstone])

(def status-encodings-schema
  [:enum :hiccup :svg :html :resource])

(def statuses-schema
  "Schema for statuses.
  | Key                | Type                              | Meaning                                                            |
  |:-------------------|:----------------------------------|:-------------------------------------------------------------------|
  | `xt/id`            | UUID                              | Row Key                                                            |
  | `primary-encoding` | keyword                           | The default post content encdoing, e.g. `:html`, `:hiccup`         |
  | `creator`          | UUID/Foreign key to `users` table | Owner of the status                                                |
  | `reply-to`         | UUID/Key for `statuses` table     | Status that this status is a reply to                              |
  | `created-at`       | Timestamp                         | The time a user created this post                                  |
  | `updated-at`       | Timestamp                         | The time a user edited this post                                   |
  | `content`          | Map                               | The content of the post                                            |
  | `ap-id`            | URI                               | ActivityPub ID/location of the status resource                     |
  | `mentions`         | Set of UUID                       | Set of user IDs mentioned in the status                            |
  | `resources`        | Set of UUID                       | Set of resource metadata IDs for each resources used in the status |
  `content` is a map where each key is a version of the post in some format, e.g. `:hiccup` for
  hiccup syntax, or `:html` for raw html, or `:svg`, etc..
  "
  {:mushin.db/statuses
   [:map
    [:type                      status-types-schema]
    [:primary-encoding          status-encodings-schema]
    [:xt/id                     :uuid]
    [:creator                   :uuid]
    [:reply-to {:optional true} :uuid]
    [:character-count           :int]
    [:ap-id                     uri?]
    [:mentions                  [:set :uuid]]
    types/created-at
    types/updated-at
    [:content                   :map]
    [:resources                 [:set :string]]]})
