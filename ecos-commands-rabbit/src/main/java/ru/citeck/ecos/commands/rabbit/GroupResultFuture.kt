package ru.citeck.ecos.commands.rabbit

import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.CompletableFuture

class GroupResultFuture : CompletableFuture<List<CommandResult>>() {

    val results: MutableList<CommandResult> = ArrayList()

    fun flushResults() {
        super.complete(results)
    }
}
