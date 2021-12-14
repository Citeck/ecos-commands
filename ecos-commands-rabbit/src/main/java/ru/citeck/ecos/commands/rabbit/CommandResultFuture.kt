package ru.citeck.ecos.commands.rabbit

import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CommandResultFuture (
    private val timeout: Long?
) : CompletableFuture<CommandResult>() {

    override fun get(): CommandResult {
        if (timeout != null) {
            return super.get(timeout, TimeUnit.MILLISECONDS)
        }
        return super.get()
    }
}
