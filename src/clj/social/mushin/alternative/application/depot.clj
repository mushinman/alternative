(ns social.mushin.alternative.application.depot)


(defprotocol Depot
  "Generic interface for dealing with application state.

  # Arguments
  The `opts` map is of the following format:
  | Key        | Type | Meaning                                                                                       |
  |:-----------|:-----|:----------------------------------------------------------------------------------------------|
  | `:db-opts` | Any  | Options specific to the database implementation, see this protocol's implementors for details |

  The `opts` map for transaction functions have the following additional keys:
  | Key       | Type | Meaning                                            |
  |:----------|:-----|:---------------------------------------------------|
  | `:async?` | bool | If true, queue the transaction instead of blocking |

  # Return value formats
  Unless otherwise stated, insert or upsert functions return values are in
  the following format:
  | Key    | Type | Meaning                                    |
  |:-------|:-----|:-------------------------------------------|
  | `:doc` | Any  | The document transacted on                 |
  | `:tx`  | Any  | The database implementation's return value |

  Unless stated otherwise, delete transaction return values with the following
  format:
  | Key    | Type | Meaning                                    |
  |:-------|:-----|:-------------------------------------------|
  | `:ids` | Any  | The document ids transacted on             |
  | `:tx`  | Any  | The database implementation's return value |
  If no transaction was made (e.g. because the depot could not find any documents to delete)
  then `:tx` and `:ids` may be absent.
  "
  ;; Misc.
  (db-time [d opts] "Returns the current time on the database.")

  ;; Authentication.
  (delete-expired-session [d opts] "Clean up expired session state.")
  (delete-all-session [d opts] "Clear all session state.")
  (recall-session [d selector validator opts] "Get the session that matches `selector` and `validator`.")

  (delete-session [d selector validator opts]
    "Delete a session by its `selector` and `validator`.")
  (insert-session [d session opts]
    "Commit a session to long term `session` memory. Delete any sessions that conflict with `session`.")

  ;; User.
  (-check-nickname-and-password [d nickname password opts]
    "Check a user's `nickname` and `password` for validity. Returns true if the `password` is correct for `nickname`, otherwise false.")
  (insert-local-user [d user auth opts] "Insert `user` and `auth`. Fails if a user with the same nickname already exists.")
  (-deactivate-user [d id opts] "Deactivate a user with `id`. Action is a no-op if such a user does not exist.")
  (-search-user [d search-term opts] "Search for a user with a string `search-term`.")
  (get-by-nickname-or-id [d id-or-nickname rows opts] "Get a user by their `nickname` or `id`.

`rows` is one of the following:
- `:display`: Return the user's display data
- `:actor`: Return the user's actor data, permissions, and any non-authentication rows used for logic
- `:full`: Return both the user's display and actor data")
  (user-exists? [d id-or-nickname opts] "Return true if the user with `id-or-nickname` exists, false if not.")

  ;; Relationships.
  (get-relationships-for-actor [d actor-id rel-types opts]
    "Get relationships for `actor-id`. `rel-types` is either a keyword or tuple of keywords in
`:follow`, `:mute`, `:block`, `:react`.")
  (get-relationships-for-object [d object-id relt-types opts]
    "Get relationships for `object-id`. `rels` is either a keyword or tuple of keywords in
`:follow`, `:mute`, `:block`, `:react`.")
  (get-relationship [d actor-id object-id rel-type opts]
    "Get a relationship according to its `actor-id`, `object-id`, `rel-type`; or `nil` if no relationship exists.")
  (create-relationship [d rel opts]
    "Upsert `rel` according to the identity of the relationship (i.e. the `actor-id`, `object-id`, and `type`).")
  (delete-relationship [d actor-id object-id rel-type opts]
    "Delete a relationship according to its `actor-id`, `object-id`, and `rel-type`.")

  ;; Statuses.
  (insert-status [d status opts] "Insert `status`.")

  ;; Custom.
  (upsert-custom [d custom opts] "Upsert a custom object.")
  (delete-custom [d owner-id label category opts]
    "Delete a custom object by its `label`, `category`, and `owner-id`.")
  (get-custom-by-label [d owner-id label category opts]
    "Get a custom object by its `label`, `category` and `owner-id`, or `nil` if no such document exists.")

  ;; Resource meta.
  (-insert-resource [d resource-data mime-type opts]
    "Create a reasource with a `name` and `mime-type` from `resource-data`.")
  (-get-resource-metadata-by-id [d id opts]
    "Get a resource's metadat by its `id`.")
  (-delete-resource [d id opts]
    "Delete a resource with `id`."))

(defn check-nickname-and-password
  "Check a user's `nickname` and `password` for validity.
  Returns true if the `password` is correct for `nickname`, otherwise false.

  See `Depot` for further explanation."
  ([d nickname password opts] (-check-nickname-and-password d nickname password opts))
  ([d nickname password] (check-nickname-and-password d nickname password {})))

(defn deactivate-user
  "Delete the user with id `user-id`.
  
  See `Depot` for further explanation."
  ([d user-id opts] (-deactivate-user d user-id opts))
  ([d user-id] (deactivate-user d user-id {})))


(defn insert-resource
  "Create a reasource with a `name` and `mime-type` from `resource-data`.
 
  See `Depot` for further explanation."
  ([d resource-data mime-type opts] (-insert-resource d resource-data mime-type opts))
  ([d resource-data mime-type] (insert-resource d resource-data mime-type {})))

(defn delete-resource
  "Delete a resource with `id`.
  
  See `Depot` for further explanation."
  ([d id opts] (-delete-resource d id opts))
  ([d id] (delete-resource d id {})))

(defn get-resource-metadata-by-id
  "Get a resource's metadat by its `id`.
    
  See `Depot` for further explanation."
  ([d id opts] (-get-resource-metadata-by-id d id opts))
  ([d id] (get-resource-metadata-by-id d id {})))


(defn search-user
  "Search for a user with a string `search-term`."
  ([d search-term opts] (-search-user d search-term opts))
  ([d search-term] (search-user d search-term {})))
