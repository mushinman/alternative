(ns social.mushin.alternative.depot
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [social.mushin.alternative.db.sqlite.sqlite-database :refer [create-sqlite-database]]
            [social.mushin.alternative.utils :as ig-utils])
  (:import [com.zaxxer.hikari HikariDataSource]
           [java.util.concurrent ExecutorService Executors]))

(defmethod ig/init-key :db/db
  [_ {:keys [db-spec]}]
  (log/info "Creating database connection pool..." (dissoc db-spec :password))
  (let [ds (connection/->pool HikariDataSource db-spec)]
    (when (= (:dbtype db-spec) "sqlite")
      (jdbc/execute! ds ["PRAGMA journal_mode = WAL"])
      (jdbc/execute! ds ["PRAGMA busy_timeout = 5000"])
      (create-sqlite-database ds))))

(defmethod ig/halt-key! :db/db
  [_ {:keys [ds]}]
  (log/info "Closing database connection pool...")
  (.close ^HikariDataSource ds))

(defmethod ig/init-key :serives/executor
  [_ ]
  (log/info "Creating executor...")
  (Executors/newSingleThreadExecutor))

(defmethod ig/halt-key! :services/executor
  [_ executor]
  (log/info "Shutting down exectuor...")
  (.close ^ExecutorService executor))

(defmethod ig/init-key :alternative/depot
  [_ depot]
  (log/info "Creating the depot...")
  depot)

(defmethod ig/halt-key! :alternative/depot
  [_ executor]
  (log/info "Shutting down depot..."))
