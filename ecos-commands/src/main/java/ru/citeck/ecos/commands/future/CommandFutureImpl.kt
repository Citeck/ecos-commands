package ru.citeck.ecos.commands.future

import ru.citeck.ecos.webapp.api.promise.Promise
import java.time.Duration
import java.util.concurrent.TimeUnit

class CommandFutureImpl<T> (
    private val impl: Promise<T>
) : CommandFuture<T> {

    override fun get(timeout: Long, unit: TimeUnit): T {
        return impl.get(Duration.ofMillis(unit.toMillis(timeout)))
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
        return impl.get()
    }

    override fun asPromise(): Promise<T> {
        return impl
    }
}
