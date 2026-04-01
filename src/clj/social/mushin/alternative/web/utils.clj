(ns social.mushin.alternative.web.utils
  (:require [ring.util.http-response :refer [unauthorized! conflict! service-unavailable! 
                                             internal-server-error!]]
            [clojure.tools.logging :as log]))
            


(def id-schema
  "A schema for a map with just a key called `:id` and a `:uuid` value.
  Useful for path params."
  [:map [:id :uuid]])

(defn db-error->http-error
  "Convert a database exception to a HTTP response."
  [e]
  (log/error e "Database error")
  (if-let [{:keys [type subtype] :as _} (ex-data e)]
    (case type
      :conflict
      (conflict!
       (if (= subtype :assert-failed)
         {:code :resource-already-exists
          :message "A resource with the same unique identifier already exists"}
         {:code :conflict}))

      :forbidden
      (unauthorized! {:code :unauthorized})

      :unavailable
      (service-unavailable! {:code :unavailable})

      (:incorrect :db-misc :system)
      (internal-server-error! {:code :internal-error}))
    (internal-server-error! {:type :database})))

(defmacro wrap-db-errors
  "Wrap any exceptions thrown by `form`, converting them into a HTTP error."
  [form]
  `(try
     ~form
     (catch Exception e#
       (db-error->http-error e#))))
