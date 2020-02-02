package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.Channel
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class RabbitCommandsService(
    factory: CommandsServiceFactory,
    channel: Channel
) : RemoteCommandsService {

    private val rabbitContext: RabbitContext
    private val commandsService: CommandsService = factory.commandsService
    private val properties = factory.properties

    private val commands: MutableMap<String, CompletableFuture<CommandResultDto>> = ConcurrentHashMap()

    init {
        rabbitContext = RabbitContext(
            channel,
            factory.properties,
            { onCommandReceived(it) },
            { onResultReceived(it) }
        )
    }

    private fun onResultReceived(result: CommandResultDto) {
        commands[result.command.id]?.complete(result)
    }

    private fun onCommandReceived(command: CommandDto) : CommandResultDto {
        if (command.targetApp != properties.appName) {
            throw RuntimeException("Incorrect target app name '${command.targetApp}'. Expected: ${properties.appName}")
        }
        return commandsService.execute(command).get(properties.commandTimeoutMs, TimeUnit.MILLISECONDS)
    }

    override fun execute(command: CommandDto): Future<CommandResultDto> {
        val future = CompletableFuture<CommandResultDto>()
        commands[command.id] = future
        try {
            rabbitContext.sendCommand(command)
        } catch (e: Exception) {
            commands.remove(command.id)
            throw e
        }
        return future
    }
}
