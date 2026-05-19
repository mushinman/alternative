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
  "
  ;; Misc.
  (-db-time [d opts] "Returns the current time on the database.")

  ;; Session state.
  (-delete-expired-session [d opts] "Clean up expired session state.")
  (-delete-all-session [d opts] "Clear all session state.")
  (-delete-session [d session-id opts] "Delete a session by its `session-id`.")
  (-insert-session [d session opts] "Commit a session to long term `session` memory.
Delete any sessions that conflict with `session`.")
  (-update-session [d session old-session-id opts] "Delete session with id `session-id` if it exists,
and commit `session` to session state.")
  (-recall-session [d selector validator opts] "Get the session that matches `selector` and `validator`.")

  ;; User.
  (-check-nickname-and-password [d nickname password opts] "Check a user's `nickname` and `password` for validity.
Returns true if the `password` is correct for `nickname`, otherwise false.")
  (insert-local-user [d user actor auth opts] "Insert `user`, `actor`, and `auth`. Fails if a user with the same nickname already exists.")
  (-deactivate-user [d id opts] "Deactivate a user with `id`. Action is a no-op if such a user does not exist.")
  (-search-user [d search-term opts] "Search for a user with a string `search-term`.")
  (get-by-nickname-or-id [d id-or-nickname rows opts] "Get a user by their `nickname` or `id`, along with its actor information.

`rows` is one of the following:
- `:display`: Return the user's display data
- `:actor`: Return the user's actor data, permissions, and any non-authentication rows used for logic
- `:full`: Return both the user's display and actor data")
  (user-exists? [d id-or-nickname opts] "Return true if the user with `id-or-nickname` exists, false if not.")

  ;; Statuses.
  (insert-status [d status opts] "Insert `status`.")


  ;; Resource meta.
  (-insert-resource [d resource-data mime-type opts]
    "Create a reasource with a `name` and `mime-type` from `resource-data`.")
  (-get-resource-metadata-by-id [d id opts]
    "Get a resource's metadat by its `id`.")
  (-delete-resource [d id opts]
    "Delete a resource with `id`."))

(defn db-time
  "Returns the current time on the database.

  See `Depot` for further explanation."
  ([d opts] (-db-time d opts))
  ([d] (-db-time d {})))

(defn check-nickname-and-password
  "Check a user's `nickname` and `password` for validity.
  Returns true if the `password` is correct for `nickname`, otherwise false.

  See `Depot` for further explanation."
  ([d nickname password opts] (-check-nickname-and-password d nickname password opts))
  ([d nickname password] (check-nickname-and-password d nickname password {})))

(defn insert-session
  "Commit a session to long term `session` memory.
  Delete any sessions that conflict with `session`.

  See `Depot` for further explanation."
  ([d session opts] (-insert-session d session opts))
  ([d session] (insert-session d session {})))

(defn update-session
  "Delete session with id `session-id` if it exists,
  and commit `session` to session state.

  See `Depot` for further explanation."
  ([d session session-id opts] (-update-session d session session-id opts))
  ([d session session-id] (update-session d session session-id {})))

(defn recall-session
  "Get the session that matches `selector` and `validator`.

  See `Depot` for further explanation."
  ([d selector validator opts] (-recall-session d selector validator opts))
  ([d selector validator] (recall-session d selector validator {})))

(defn delete-session
  "Delete a session by its `session-id`.

  See `Depot` for further explanation."
  ([d session-id opts] (-delete-session d session-id opts))
  ([d session-id] (delete-session d session-id {})))

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
