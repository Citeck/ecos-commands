package ru.citeck.ecos.commands.future

import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.PromiseTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CommandFutureImpl<T> (
    private val impl: Promise<T>
) : CommandFuture<T> {

    override fun get(timeout: Long, unit: TimeUnit): T {
        try {
            return impl.get(Duration.ofMillis(unit.toMillis(timeout)))
        } catch (e: PromiseTimeoutException) {
            throw TimeoutException(e.message)
        }
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
        try {
            return impl.get()
        } catch (e: PromiseTimeoutException) {
            throw TimeoutException(e.message)
        }
    }

    override fun asPromise(): Promise<T> {
        return impl
    }
}
