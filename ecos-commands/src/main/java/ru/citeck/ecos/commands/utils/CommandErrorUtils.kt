package ru.citeck.ecos.commands.utils

import ru.citeck.ecos.commands.dto.CommandError
import java.util.*

object CommandErrorUtils {

    fun convertException(exception: Exception): CommandError {

        var throwable: Throwable? = exception
        while (throwable!!.cause != null) {
            throwable = throwable.cause
        }
        val errorStackTrace: MutableList<String> = ArrayList()
        val stackTrace = throwable.stackTrace
        if (stackTrace != null) {
            var i = 0
            while (i < 3 && i < stackTrace.size) {
                errorStackTrace.add(stackTrace[i].toString())
                i++
            }
        }
        return CommandError(
            type = throwable.javaClass.simpleName,
            message = throwable.localizedMessage ?: "",
            stackTrace = errorStackTrace
        )
    }
}
