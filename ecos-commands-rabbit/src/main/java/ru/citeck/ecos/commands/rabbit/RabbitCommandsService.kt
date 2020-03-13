package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.Channel
import mu.KotlinLogging
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.utils.WeakValuesMap
import java.lang.IllegalArgumentException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

private val log = KotlinLogging.logger {}

class RabbitCommandsService(
    private val factory: CommandsServiceFactory,
    private val channel: Channel
) : RemoteCommandsService {

    private lateinit var rabbitContext: RabbitContext
    private val commandsService: CommandsService = factory.commandsService
    private val properties = factory.properties

    private val commands = WeakValuesMap<String, CompletableFuture<CommandResult>>()
    private val commandsForGroup = WeakValuesMap<String, GroupResultFuture>()

    private val timer = Timer("RabbitCommandsTimer", false)

    private var initialized = false

    override fun init() {
        if (initialized) {
            return
        }
        rabbitContext = RabbitContext(
            channel,
            { onCommandReceived(it) },
            { onResultReceived(it) },
            factory.properties
        )
        initialized = true
    }

    private fun onResultReceived(result: CommandResult) {
        val com = commands.get(result.command.id)
        if (com != null) {
            commands.get(result.command.id)?.complete(result)
        } else {
            commandsForGroup.get(result.command.id)?.results?.add(result)
        }
    }

    private fun onCommandReceived(command: Command) : CommandResult {
        if (command.targetApp != properties.appName && command.targetApp != "all") {
            throw RuntimeException("Incorrect target app name '${command.targetApp}'. " +
                                   "Expected: '${properties.appName}' OR 'all'")
        }
        return commandsService.executeLocal(command)
    }

    override fun executeForGroup(command: Command, config: CommandConfig): Future<List<CommandResult>> {
        val ttlMs = config.ttl.toMillis()
        if (ttlMs <= 0 && ttlMs > TimeUnit.MINUTES.toMillis(10)) {
            throw IllegalArgumentException("Illegal ttl for group command: $ttlMs")
        }
        val future = GroupResultFuture()
        commandsForGroup.put(command.id, future)
        if (commandsForGroup.size() > 10_000) {
            log.warn { "CommandsForGroup size is too bit. Potentially memory leak. Size: " + commandsForGroup.size() }
        }
        try {
            rabbitContext.sendCommand(command, config)
        } catch (e: Exception) {
            commandsForGroup.remove(command.id)
            throw e
        }
        timer.schedule(ttlMs) {
            future.flushResults()
        }
        return future
    }

    override fun execute(command: Command, config: CommandConfig): Future<CommandResult> {
        val future = CompletableFuture<CommandResult>()
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
