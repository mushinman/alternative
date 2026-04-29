(ns social.mushin.alternative.application.statuses
  (:require [social.mushin.alternative.db.statuses :as db-st]
            [social.mushin.alternative.utils :refer [grapheme-count]]
            [social.mushin.alternative.application.depot :as depot]))

;; TODO come back to this.
;; Probably need to normalize content somewhat... like compress multiple newlines into one newline, etc..
(defn create-text-status
  ([depot creator-id content resources mentions db-opts]
   (let [status (db-st/create-local-status creator-id [:p content] resources
                                           mentions (grapheme-count content)
                                           :hiccup :text)]
     (depot/insert-status depot status db-opts)))
  ([depot creator-id content resources mentions]
   (create-text-status depot creator-id content resources mentions {})))
