(ns social.mushin.alternative.validators
  (:import [org.apache.commons.validator.routines EmailValidator]))

(def ^:private email-validator-instance
  "Email validator object."
  (proxy [EmailValidator] [false true]
    (isValid [^String email] (proxy-super isValid email))
    (isValidDomain [^String domain] (proxy-super isValidDomain domain))
    (isValidUser [^String user] (proxy-super isValidUser user))))

(defn is-email-valid?
  "Validates an email address."
  ^Boolean
  [^String email]
  (.isValid email-validator-instance email))

(defn is-email-user-valid?
  "Validates just the username segment of an email address (before the @)."
  ^Boolean
  [^String user]
  (.isValidUser email-validator-instance user))


(defn is-domain-valid?
  "Validates a domain name."
  ^Boolean
  [^String user]
  (.isValidUser email-validator-instance user))
