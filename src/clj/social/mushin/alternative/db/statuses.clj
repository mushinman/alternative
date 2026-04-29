(ns social.mushin.alternative.db.statuses
  (:require [social.mushin.alternative.db.types :as types]
            [clj-uuid :as uuid]
            [java-time.api :as t]))


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
    [:xt/id                     :uuid]
    [:type                      status-types-schema]
    [:primary-encoding          status-encodings-schema]
    [:creator                   :uuid]
    [:reply-to {:optional true} :uuid]
    [:character-count           :int]
    ;[:ap-id                     types/uri-schema]
    [:mentions                  [:set :uuid]]
    types/created-at
    types/updated-at
    [:content                   :map]
    [:resources                 [:set :string]]]})


(defn create-local-status
  ([creator-id content resources mentions character-count primary-encoding type reply-to]
   (let [now (t/zoned-date-time)]
     (cond-> {:xt/id
              ;; Allocate a UUID such that it shares the first 32 bits with the creator's id.
              (let [base (uuid/v4)]
                (uuid/v4 (bit-or (bit-and
                                  (uuid/get-word-high creator-id)
                                  (bit-shift-left 0xFFFFFFFF 32))
                                 (bit-and
                                  (uuid/get-word-high base)
                                  0xFFFFFFFF))
                         (uuid/get-word-low base)))
              :creator creator-id
              :type type
              :content content
              :resources resources
              :mentions mentions
              :character-count character-count
              :primary-encoding primary-encoding
              :created-at now
              :updated-at now}
       reply-to (assoc :reply-to reply-to))))
  ([creator-id content resources mentions character-count primary-encoding type]
   (create-local-status creator-id content resources mentions character-count primary-encoding type nil)))
