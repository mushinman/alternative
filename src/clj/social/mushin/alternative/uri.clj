(ns social.mushin.alternative.uri
  (:require [clojure.string :as str]
            [social.mushin.alternative.utils :refer [when-some?]])
  (:import [java.net URI URLEncoder]
           [java.nio.charset StandardCharsets]
           [java.nio.file Path]
           [java.io File]))

(defn url-encode
  "URL encode a string."
  ^String
  [^String s]
  (URLEncoder/encode s StandardCharsets/UTF_8))

(defn- url-encode-params
  "URL encode a params map into a single string."
  ^String
  [m]
  (if (string? m)
    m
    (str/join "&" (for [[k v] m
                        ;; If the value is a sequence, flatten it up to one level, using the same
                        ;; param name for each value.
                        vn (if (and (not (string? v)) (seqable? v)) v [v])
                        :let [param-name (url-encode (str (if (keyword? k)
                                                            (symbol k)
                                                            k)))]]
                    (str param-name "=" (url-encode (str vn)))))))

(defn- internal-join
  "Implement `join`."
  ^URI
  [^URI uri [first & rest]]
  (let [resolved-uri (if (uri? first)
                       (.resolve uri ^URI first)
                       (.resolve uri (str first)))]
    (if (empty? rest)
      resolved-uri
      (recur resolved-uri rest))))


(defn uri
  "Coerce a value to a URI. Accepts maps by URI parts. See `to-uri-map` for its format."
  ^URI
  [uri-like]
  (cond
    (uri? uri-like) uri-like

    (map? uri-like)
    (let [{:keys [port host scheme user password path query fragment user-info]} uri-like]
      (URI.
       ^String
       (when-some? scheme (str scheme))
       ^String
       (cond
         (some? user-info) (str user-info)
         (and (some? user) (some? password)) (str user ":" password)
         :else nil)
       ^String
       (when-some? host (str host))
       ^Integer
       (cond
         (number? port) (int port)
         (string? port) (Integer/parseInt port)
         :else (int -1))
       ^String
       (when-some? path (str path))
       ^String
       (cond
         (associative? query) (url-encode-params query)
         (string? query) query
         :else nil)
       ^String
       (when-some? fragment (str fragment))))

    (instance? File uri-like) (.toURI ^File uri-like)

    (instance? Path uri-like) (.toURI ^Path uri-like)

    :else (URI. (str uri-like))))

(defn to-uri-map
  "Convert a URI to a map of each of its parts. Only the defined parts will be present in the map.

  The return value is accepted by `uri`.

  The return map may have the values `:port`, `:path`, `:host`, `:fragment`, `:query`, `:scheme`, `:user`, `:password`."
  [uri-like]
  (let [v (uri uri-like)
        m (cond-> {}
            (> (.getPort v) -1) (assoc :port (.getPort v))
            (not (str/blank? (.getPath v))) (assoc :path (.getPath v))
            (.getHost v) (assoc :host (.getHost v))
            (.getQuery v) (assoc :query (.getQuery v))
            (.getFragment v) (assoc :fragment (.getFragment v))
            (.getScheme v) (assoc :scheme (.getScheme v)))]
    (if-let [user-info (.getUserInfo v)]
      (let [[username password] (str/split user-info #":")]
        (assoc m :user username :password password))
      m)))

(defn assoc-uri
  "Merge the two uri-likes by their parts, taking `other-uri-like`'s parts on conflict."
  ^URI
  [base-uri-like other-uri-like]
  (uri (merge (to-uri-map base-uri-like) (to-uri-map other-uri-like))))

(defn join
  "Join URI coercables together into a single URI."
  ^URI
  [base-uri & paths]
  (internal-join (uri base-uri) paths))

(defn absolute?
  "`true` if `uri` is absolute, else `false`."
  ^Boolean
  [uri-like]
  (.isAbsolute (uri uri-like)))
