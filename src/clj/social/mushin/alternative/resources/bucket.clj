(ns social.mushin.alternative.resources.bucket
  (:require [social.mushin.alternative.files :as files]
            [social.mushin.alternative.codecs :as codecs]
            [social.mushin.alternative.digest :as digest]
            [social.mushin.alternative.mime :as mime]
            [clojure.java.io :as io]
            [social.mushin.alternative.multimedia.img :as img]
            [social.mushin.alternative.multimedia.gif :as gif])
  (:import [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [java.io InputStream]))

;; TODO wrap resource exceptions.
(defprotocol Bucket
  (-create! [this name resource-data mime-type]
    "Create a resource with a `name` and `mime-type` from `resource-data`.

Returns a URI to the resource.")
  (-open [this name]
    "Get a stream that references the resource referenced by `name`.")
  (-exists? [this name]
    "Returns true if a resource referenced by `name` exists, false if not.")
  (-delete! [this name]
    "Delete the resource referenced by `name`.  If the resource doesn't exist this operation is a no-op.
Return true on successful, false if the resource does not exist.")
  (-get-uri-to [this name]
    "Return a URI to the resource with `name` if it exists; else `nil`."))


(defn open
  [b name]
  (-open b name))

(defn delete!
  [b name]
  (-delete! b name))

(defn get-uri-to
  [b name]
  (-get-uri-to b name))

(defn- create-resource-from-buffered-image!
  "Create and store a buffered image as a resource.

  # Arguments
  - `img`: The image to store.
  - `mime-type`: The MIME type of the resulting stored image.
  - `bucket`: The resource provider to store the image.

  # Return
  A URI to the stored image. The resource name will be based off the SHA-256 sum
  of the image's sRGB content."
  [^BufferedImage img mime-type bucket]
  (let [mime-ext (mime/mime-type-to-extensions mime-type)
        resource-name (str (codecs/bytes->b64u (img/checksum-image img)) "." mime-ext)]
    (if-let [resource-location (get-uri-to bucket resource-name)]
      resource-location
      (let [output-file-path (files/create-temp-file)]
        (try
          (with-open [temp-output-file (io/output-stream (str output-file-path))
                      image-ios (ImageIO/createImageOutputStream temp-output-file)]
            (img/write-img-from-mime-type img mime-type image-ios))
          (-create! bucket resource-name output-file-path mime-type)
          (finally
            (files/delete-if-exists output-file-path)))))))

(defn create-resource-from-static-image!

  "Create and store an image as a resource.

  # Arguments
  - `img`: The image to store.
  - `mime-type`: The MIME type of the resulting stored image.
  - `bucket`: The resource provider to store the image.

  # Return
  A URI to the stored image. The resource name will be based off the SHA-256 sum
  of the image's sRGB content."
  [img-path mime-type bucket]
  (create-resource-from-buffered-image! (img/->buffered-image (str img-path))
                                        mime-type
                                        bucket))

(defn create-resource-for-gif!
  [^InputStream gif-stream bucket]
  (let [gif (gif/get-gif-from-stream gif-stream)
        resource-name (str (loop [i 0
                                  md (digest/create-sha256-digest)
                                  scenes (:scenes gif)]
                             ;; Digest every frame.
                             ;; TODO similar to the static frames we probably want to add some metadata
                             ;; to the checksum, like if it loops or not.
                             (if-not (= i (count gif))
                               (do (img/digest-img! (:frame (nth scenes i)) md)
                                   (recur (inc i) md scenes))
                               (digest/digest->b64u md)))
                           ".gif")]
    (if-let [resource-location (get-uri-to bucket resource-name)]
      resource-location
      (let [output-file-path (files/create-temp-file "" "")]
        (try
          (with-open [temp-output-file (io/output-stream (str output-file-path))
                      image-ios (ImageIO/createImageOutputStream temp-output-file)]
            (gif/write-gif-to-stream image-ios gif))
          (-create! bucket resource-name output-file-path "image/gif")
          (finally
            (files/delete-if-exists output-file-path)))))))

