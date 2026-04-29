package social.mushin.alternative.hosted.application

import social.mushin.alternative.hosted.application.error.AlternativeException
import clojure.lang.ExceptionInfo
import clojure.lang.IPersistentMap
import clojure.lang.PersistentArrayMap
import clojure.lang.Keyword

class AlternativeApplicationException(
    message: String,
    code: Keyword,
    data: IPersistentMap = PersistentArrayMap.EMPTY,
    cause: Throwable? = null
) : AlternativeException(message, code, data, cause)
