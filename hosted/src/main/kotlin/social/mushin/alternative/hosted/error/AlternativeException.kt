package social.mushin.alternative.hosted.application.error

import clojure.lang.ExceptionInfo
import clojure.lang.IPersistentMap
import clojure.lang.PersistentArrayMap
import clojure.lang.Keyword

abstract class AlternativeException(
    message: String,
    code: Keyword,
    data: IPersistentMap = PersistentArrayMap.EMPTY,
    cause: Throwable? = null
) : ExceptionInfo(message, data, cause) {
    val code = code
}
