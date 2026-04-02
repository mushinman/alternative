(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [clojure.pprint]
    [clojure.spec.alpha :as s]
    [social.mushin.alternative.db.users :as users]
    [clojure.tools.namespace.repl :as repl]
    [criterium.core :as c]                                  ;; benchmarking
    [expound.alpha :as expound]
    [integrant.core :as ig]
    [clojure.java.io :as io]
    [integrant.repl :refer [clear go halt prep init reset reset-all]]
    [integrant.repl.state :as state]
    [kit.api :as kit]
    [lambdaisland.classpath.watch-deps :as watch-deps]      ;; hot loading for deps
    [social.mushin.alternative.core :refer [start-app]]))

;; uncomment to enable hot loading for deps
;;

(defn dev-depot [] (:social.mushin.alternative.depot/db state/system))


(watch-deps/start! {:aliases [:dev :test]})

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(add-tap (bound-fn* clojure.pprint/pprint))

;; TODO test the sign tokens use in a release build/make sure the require works.

(defn dev-prep!
  []
  (integrant.repl/set-prep! (fn []
                              (require 'social.mushin.alternative.web.sign)
                              (require 'social.mushin.alternative.db.tasks)
                              (-> (social.mushin.alternative.config/system-config {:profile :dev})
                                  (ig/expand)))))

(defn test-prep!
  []
  (integrant.repl/set-prep! (fn []
                              (require 'social.mushin.alternative.web.sign)
                              (-> (social.mushin.alternative.config/system-config {:profile :test})
                                  (ig/expand)))))

;; Can change this to test-prep! if want to run tests as the test profile in your repl
;; You can run tests in the dev profile, too, but there are some differences between
;; the two profiles.
(dev-prep!)

(repl/set-refresh-dirs "src/clj")

(def refresh repl/refresh)


(defn inject-test-data
  [node path]
  )

(comment
  (go)
  (reset))
