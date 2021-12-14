package ru.citeck.ecos.commands.rabbit

import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GroupCommandResultFuture(
    private val timeout: Long?
) : CompletableFuture<List<CommandResult>>() {

    val results: MutableList<CommandResult> = ArrayList()

    override fun get(): List<CommandResult> {
        if (timeout != null) {
            return super.get(timeout, TimeUnit.MILLISECONDS)
        }
        return super.get()
    }

    fun flushResults() {
        super.complete(results)
    }
}
