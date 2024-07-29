package ru.citeck.ecos.commands.rabbit

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class GroupResultFuture(private val command: Command) : CompletableFuture<List<CommandResult>>() {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    val results: MutableList<CommandResult> = CopyOnWriteArrayList()

    fun flushResults() {
        // Not sure why but sometimes this list contain null values
        // maybe cause of it was non thread safe list for 'results' variable.
        // Now it thread safe, and null filtering may be removed if logs doesn't
        // contain warnings 'Some results is null'
        if (results.any { it == null }) {
            log.warn { "Some results is null. Command: $command" }
            super.complete(results.filterNotNull())
        } else {
            super.complete(results)
        }
    }
}
