package ru.citeck.ecos.commands.remote

import ecos.com.fasterxml.jackson210.databind.node.NullNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandError
import ru.citeck.ecos.commands.dto.CommandResult
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class NoopRemoteCommandsService(factory: CommandsServiceFactory) : RemoteCommandsService {

    private val webappProps = factory.webappProps

    companion object {
        val log: Logger = LoggerFactory.getLogger(NoopRemoteCommandsService::class.java)
    }

    override fun execute(command: Command): Future<CommandResult> {
        val errorMsg = "Remote commands service is not defined. Command can't be executed"
        log.error("$errorMsg. Command: $command")
        return CompletableFuture.completedFuture(
            CommandResult(
                id = UUID.randomUUID().toString(),
                result = NullNode.instance,
                command = command,
                started = Instant.now().toEpochMilli(),
                completed = Instant.now().toEpochMilli(),
                errors = listOf(
                    CommandError(
                        type = "",
                        message = errorMsg
                    )
                ),
                appName = webappProps.appName,
                appInstanceId = webappProps.appInstanceId
            )
        )
    }

    override fun executeForGroup(command: Command): Future<List<CommandResult>> {
        return CompletableFuture.completedFuture(listOf(execute(command).get(1, TimeUnit.MINUTES)))
    }

    override fun dispose() {
    }
}
