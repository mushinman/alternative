(ns social.mushin.alternative.errors)

(defn wrap-throw
  ([msg ctx cause]
   (throw (ex-info msg {:type ::error :context ctx} cause)))
  ([msg ctx]
   (throw (ex-info msg {:type ::error :context ctx}))))
