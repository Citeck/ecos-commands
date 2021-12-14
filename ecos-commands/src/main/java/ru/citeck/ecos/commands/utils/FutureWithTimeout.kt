package ru.citeck.ecos.commands.utils

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class FutureWithTimeout<T> (
    private val impl: Future<T>,
    private val timeoutMs: Long?
) : Future<T> {

    override fun get(timeout: Long, unit: TimeUnit): T {
        return impl.get(timeout, unit)
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return impl.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
        return impl.isCancelled
    }

    override fun isDone(): Boolean {
        return impl.isDone
    }

    override fun get(): T {
        if (timeoutMs != null) {
            return get(timeoutMs, TimeUnit.MILLISECONDS)
        }
        return impl.get()
    }
}
