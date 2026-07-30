package social.mushin.alternative.hosted.db.sqlite

import org.sqlite.Function
import org.sqlite.SQLiteConnection
import java.sql.Connection
import clojure.lang.IFn


class SQLiteFunction(val function: IFn) : Function() {

    fun argumentCount(): Int {
        return args()
    }

    
    fun throwError(err: String) {
        error(err)
    }

    fun returnVoid() {
        result()
    }

    fun returnBytes(returnValue: ByteArray) {
        result(returnValue)
    }

    fun returnDouble(returnValue: Double) {
        result(returnValue)
    }

    fun returnInt(returnValue: Int) {
        result(returnValue)
    }

    fun returnLong(returnValue: Long) {
        result(returnValue)
    }

    fun returnString(returnValue: String) {
        result(returnValue)
    }

    fun getNthArgAsDouble(nArg: Int): Double {
        return value_double(nArg)
    }

    fun getNthArgAsInt(nArg: Int): Int {
        return value_int(nArg)
    }

    fun getNthArgAsLong(nArg: Int): Long {
        return value_long(nArg)
    }

    fun getNthArgAsString(nArg: Int): String {
        return value_text(nArg)
    }

    fun getNthArgType(nArg: Int): Int {
        return value_type(nArg)
    }

    override fun xFunc() {
        function.invoke(this)
    }
}
