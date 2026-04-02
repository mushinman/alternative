(ns social.mushin.alternative.resources.file-system-bucket
  (:require [social.mushin.alternative.files :as files]
            [clojure.string :as cstr]
            [social.mushin.alternative.resources.bucket :as interface]
            [lambdaisland.uri :refer [join]]
            [clojure.java.io :as io])
  (:import [java.nio.file Path]
           [java.io InputStream]))

(defn- get-resource-path-as-vec
  [resource-name]
  (let [name-length (count resource-name)]
    [(if (> name-length 1) (subs resource-name 0 2) (subs resource-name 0 1))
     (if (> name-length 3) (subs resource-name 2 4) "")
     (if (> name-length 5) (subs resource-name 4 6) "")
     resource-name]))

(defn- get-resource-file-path
  "For a given base file path to the resource folder and the name of the resource
  generate a unique file path for the resource.
  # Arguments:
  - `base`: (Optional) A java Path to the resources folder.
  - `resource-name`: The name of the resource.

  # Return value
  A unique java Path for the provided resource name. If `base` is not provided
  the path is only partial."
  ^Path
  [base resource-name]
  (apply files/path-combine base (get-resource-path-as-vec resource-name)))


(defrecord FileSystemBucket
    [base-path resource-map-url-base]
  interface/Bucket
  (create! [this name resource-data _]
    (let [resource-path (get-resource-file-path base-path name)
          resource-path-str (str resource-path)]
      (if-let [uri-to-resource (interface/get-uri-to this name)]
        uri-to-resource
        (do
          (io/make-parents resource-path-str)
          (cond
            (instance? InputStream resource-data)
            ;; Atomically move InputStream's data to a file at the resource's path.
            (let [temp-file (files/create-temp-file)]
              (try
                (with-open [output-file (io/output-stream (files/coerce-to-file temp-file))]
                  (io/copy resource-data output-file))
                (files/move temp-file resource-path)
                (finally
                  (files/delete-if-exists temp-file))))

            ;; Assume coerceible to a path.
            :else
            (files/copy resource-data resource-path))
          (interface/get-uri-to this name)))))
  (delete! [_ name]
    (files/delete-if-exists (get-resource-file-path base-path name)))
  (exists? [_ name]
    (files/exists? (get-resource-path-as-vec name)))
  (get-uri-to [_ name]
    (let [file-path (get-resource-path-as-vec name)]
      (when (files/exists? file-path)
        (join resource-map-url-base (cstr/join "/" file-path)))))
  (open [_ name]
    (io/input-stream (str (get-resource-file-path base-path name)))))
