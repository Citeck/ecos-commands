package ru.citeck.ecos.commands.future

import ru.citeck.ecos.webapp.api.promise.Promise
import java.util.concurrent.Future

interface CommandFuture<out T> : Future<@UnsafeVariance T> {

    fun asPromise(): Promise<T>
}
