package ru.citeck.ecos.commands.future

import mu.KotlinLogging
import ru.citeck.ecos.commons.promise.PromiseException
import ru.citeck.ecos.webapp.api.promise.Promise
import java.time.Duration
import java.util.concurrent.TimeUnit

class CommandFutureImpl<T> (
    private val impl: Promise<T>
) : CommandFuture<T> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        return handleGet { impl.get(Duration.ofMillis(unit.toMillis(timeout))) }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        return false
    }

    override fun isDone(): Boolean {
        return impl.isDone()
    }

    override fun get(): T {
        return handleGet { impl.get() }
    }

    override fun asPromise(): Promise<T> {
        return impl
    }

    private inline fun handleGet(crossinline getImpl: () -> T): T {
        return try {
            getImpl()
        } catch (e: PromiseException) {
            // debug because this stacktrace will print full trace include
            // cause and this may create too much duplicates in logs
            log.debug(e) { "Promise exception" }
            // for backward compatibility
            throw e.cause ?: e
        }
    }
}
