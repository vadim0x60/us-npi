(ns usnpi.db
  (:require [clojure.java.jdbc :as jdbc]
            [clj-time.jdbc] ;; extends JDBC protocols
            [honeysql.core :as sql]
            [clojure.tools.logging :as log]
            [migratus.core :as migratus]
            [usnpi.env :refer [env]]))


(def ^:private
  db-url
  (format "jdbc:postgresql://%s:%s/%s?stringtype=unspecified&user=%s&password=%s"
          (:db-host env)
          (:db-port env)
          (:db-database env)
          (:db-user env)
          (:db-password env)))

(def ^:dynamic
  *db* {:dbtype "postgresql"
        :connection-uri db-url})

(defn to-sql
  "Local wrapper to turn a map into a SQL string."
  [sqlmap]
  (sql/format sqlmap))

;;
;; DB API
;; Here and below: partial doesn't work with binding.
;;

(defn query [& args]
  (apply jdbc/query *db* args))

(defn get-by-id [& args]
  (apply jdbc/get-by-id *db* args))

(defn find-by-keys [& args]
  (apply jdbc/find-by-keys *db* args))

(defn insert! [& args]
  (first (apply jdbc/insert! *db* args)))

(defn insert-multi! [& args]
  (apply jdbc/insert-multi! *db* args))

(defn update! [& args]
  (apply jdbc/update! *db* args))

(defn delete! [& args]
  (apply jdbc/delete! *db* args))

(defn execute! [& args]
  (apply jdbc/execute! *db* args))

(defmacro with-tx
  "Runs a series of queries into transaction."
  [& body]
  `(jdbc/with-db-transaction [tx# *db*]
     (binding [*db* tx#]
       ~@body)))

(defmacro with-tx-test
  "The same as `with-tx` but rolls back the transaction after all."
  [& body]
  `(with-trx
     (jdbc/db-set-rollback-only! *db*)
     ~@body))


;;
;; Custom queries
;;

(defn query-insert-practitioners
  [values]
  (let [query-map {:insert-into :practitioner
                   :values values}
        extra "ON CONFLICT (id) DO UPDATE SET deleted = EXCLUDED.deleted, resource = EXCLUDED.resource"
        query-vect (sql/format query-map)
        query-main (first query-vect)
        query-full (format "%s %s" query-main extra)]
    (into [query-full] (rest query-vect))))

;;
;; migrations
;;

(def ^:private
  mg-cfg {:store :database
          :migration-dir "migrations"
          :db *db*})

(defn- migrate []
  (log/info "Running migrations...")
  (migratus/migrate mg-cfg)
  (log/info "Migrations done."))

;;
;; Init part
;;

(defn init []
  (migrate))
