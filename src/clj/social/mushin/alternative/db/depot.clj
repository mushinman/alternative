(ns social.mushin.alternative.db.depot)


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

  ;; User queries.
  (-check-nickname-and-password [d nickname password opts] "Check a user's `nickname` and `password` for validity.
Returns true if the `password` is correct for `nickname`, otherwise false.")
  (-delete-user [d user-id opts] "Delete the user with id `user-id`.")
  (-insert-user [d user opts] "Insert `user`. Fails if a user with the same nickname already exists.")


  ;; Resource meta.
  (-insert-resource-metadata [d resource-metadata] "Insert `resource-metadata`")
  (-get-resource-metadata-by-id [d id] "")
  (-delete-resource-metadata [d id] ""))

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

(defn insert-user
  "nsert `user`. Fails if a user with the same nickname already exists.

  See `Depot` for further explanation."
  ([d user opts] (-insert-user d user opts))
  ([d user] (insert-user d user {})))
