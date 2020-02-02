package ru.citeck.ecos.commands.utils

import ru.citeck.ecos.commands.dto.ErrorDto
import java.util.*

object ErrorUtils {

    fun convertException(exception: Exception): ErrorDto {

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
        return ErrorDto(
            type = throwable.javaClass.simpleName,
            message = throwable.localizedMessage,
            stackTrace = errorStackTrace
        )
    }
}
