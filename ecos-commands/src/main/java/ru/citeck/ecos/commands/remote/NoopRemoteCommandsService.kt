package ru.citeck.ecos.commands.remote

import com.fasterxml.jackson.databind.node.NullNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandError
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import java.time.Instant
import java.util.*

class NoopRemoteCommandsService(factory: CommandsServiceFactory) : RemoteCommandsService {

    private val webappProps = factory.webappProps

    companion object {
        val log: Logger = LoggerFactory.getLogger(NoopRemoteCommandsService::class.java)
    }

    override fun execute(command: Command): Promise<CommandResult> {
        val errorMsg = "Remote commands service is not defined. Command can't be executed"
        log.error("$errorMsg. Command: $command")
        val result = CommandResult(
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
        return Promises.resolve(result)
    }

    override fun executeForGroup(command: Command): Promise<List<CommandResult>> {
        return Promises.all(listOf(execute(command)))
    }

    override fun dispose() {
    }
}
