package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.Channel
import mu.KotlinLogging
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.utils.WeakValuesMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val log = KotlinLogging.logger {}

class RabbitCommandsService(
    factory: CommandsServiceFactory,
    channel: Channel
) : RemoteCommandsService {

    private val rabbitContext: RabbitContext
    private val commandsService: CommandsService = factory.commandsService
    private val properties = factory.properties

    private val commands = WeakValuesMap<String, CompletableFuture<CommandResultDto>>()

    init {
        rabbitContext = RabbitContext(
            channel,
            { onCommandReceived(it) },
            { onResultReceived(it) },
            factory.properties
        )
    }

    private fun onResultReceived(result: CommandResultDto) {
        commands.get(result.command.id)?.complete(result)
    }

    private fun onCommandReceived(command: CommandDto) : CommandResultDto {
        if (command.targetApp != properties.appName) {
            throw RuntimeException("Incorrect target app name '${command.targetApp}'. Expected: ${properties.appName}")
        }
        return commandsService.executeLocal(command)
    }

    override fun execute(command: CommandDto, config: CommandConfig): Future<CommandResultDto> {
        val future = CompletableFuture<CommandResultDto>()
        commands.put(command.id, future)
        if (commands.size() > 10_000) {
            log.warn { "Commands size is too big. Potentially memory leak. Size: " + commands.size() }
        }
        try {
            rabbitContext.sendCommand(command, config)
        } catch (e: Exception) {
            commands.remove(command.id)
            throw e
        }
        return future
    }
}
