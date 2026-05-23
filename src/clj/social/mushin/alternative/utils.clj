(ns social.mushin.alternative.utils
  (:require [integrant.core :as ig]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
    
  (:import [java.net URI]
           [java.text BreakIterator]
           [java.nio.file Files LinkOption Paths]))

(defn to-java-uri
  [uri]
  (URI. (str uri)))

(defn disjoint?
  "Are set1 and set2 disjoint?"
  [set1 set2]
  (let [[smaller larger] (if (< (count set1) (count set2)) [set1 set2] [set2 set1])]
    (every? #(not (contains? larger %)) smaller)))

(defn intersecting?
  "Are set1 and set2 intersecting?"
  [set1 set2]
  (not (disjoint? set1 set2)))

(defmacro contains-one-of?
  [coll & keys]
  (let [c (gensym "col__")]
    (when (seq keys)
      `(let [~c ~coll]
         (or ~@(for [k keys] `(and (contains? ~c ~k)
                                   ~k)))))))

(defn grapheme-count
  ^long
  [^String text]
  (loop [it (doto (BreakIterator/getCharacterInstance)
             (.setText text))
         n 0]
    (if (not= (.next it) BreakIterator/DONE)
      (recur it (inc n))
      n)))

(defn contains-key?
  [coll key]
  (and (contains? coll key)
       key))

(defn stringify-kw
  "Turn a keyword into a string, preserving namespace."
  [kw]
  (let [ns (namespace kw)]
    (if (nil? ns)
      (name kw)
      (str ns "/" (name kw)))))

(defn concat-kw
  "Concat a keyword and a string."
  [kw s]
  (keyword (str (stringify-kw kw) s)))

(defn icase-comp
  "Compare two string ignoring case."
  [x y]
  (if (and (string? x) (string? y))
    (.equalsIgnoreCase x y)
    (= x y)))

(defmacro do-try
  "Try x. If an exception is thrown, gobble it and return nil."
  [x]
  `(try
    ~x
    (catch Exception ~'ex
      nil)))

(defn condj [v val]
  (cond-> v val (conj val)))


;; Taken from https://github.com/kit-clj/kit/blob/bf96b3e5c07e87862416a5990cbc8d480394f754/libs/kit-core/src/kit/ig_utils.clj#L9.
(defn resume-handler
  "Useful where you don't want to reset an integrant component in development."
  [k opts old-opts old-impl]
  (log/info k "resume check. Same?" (= opts old-opts))
  (if (= opts old-opts)
    old-impl
    (do (ig/halt-key! k old-impl)
        (ig/init-key k opts))))

(defn last-modified [filename]
  (let [url (io/resource filename)]
    (if url
      (case (.getProtocol url)
        "file" (-> (.toURI url)
                   (Paths/get)
                   (Files/getLastModifiedTime (into-array LinkOption []))
                   (.toMillis))
        "jar" 0
        (throw (ex-info "Unsupported URL protocol" {:protocol (.getProtocol url)})))
      (throw (ex-info "Resource not found" {:filename filename})))))

(defmacro when-some?
  [expr form]
  `(when (some? ~expr)
     ~form))

(defmacro if-some?
  [expr then-form else-form]
  `(if (some? ~expr)
     ~then-form
     ~else-form))
