package ru.citeck.ecos.commands.rabbit

import mu.KotlinLogging
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.utils.CommandUtils
import ru.citeck.ecos.commands.utils.WeakValuesMap
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import java.lang.IllegalArgumentException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.concurrent.schedule

class RabbitCommandsService(
    private val factory: CommandsServiceFactory,
    private val rabbitConnection: RabbitMqConn
) : RemoteCommandsService {

    companion object {
        val log = KotlinLogging.logger {}
    }

    private lateinit var rabbitContext: RabbitContext
    private val commandsService: CommandsService = factory.commandsService
    private val properties = factory.properties

    private val commands = WeakValuesMap<String, CompletableFuture<CommandResult>>()
    private val commandsForGroup = WeakValuesMap<String, GroupResultFuture>()

    private val timer = Timer("RabbitCommandsTimer", false)

    private val validTargetApps = setOf(
        properties.appName,
        CommandUtils.getTargetAppByAppInstanceId(properties.appInstanceId),
        "all"
    )

    init {
        repeat(factory.properties.concurrentCommandConsumers) {

            rabbitConnection.doWithNewChannel(
                Consumer { channel ->
                    rabbitContext = RabbitContext(
                        channel,
                        { onCommandReceived(it) },
                        { onResultReceived(it) },
                        factory.properties
                    )
                }
            )
        }
    }

    private fun onResultReceived(result: CommandResult) {
        val com = commands.get(result.command.id)
        if (com != null) {
            commands.get(result.command.id)?.complete(result)
        } else {
            commandsForGroup.get(result.command.id)?.results?.add(result)
        }
    }

    private fun onCommandReceived(command: Command): CommandResult? {
        if (!validTargetApps.contains(command.targetApp)) {
            throw RuntimeException(
                "Incorrect target app name '${command.targetApp}'. " +
                    "Expectedone of $validTargetApps"
            )
        }
        if (command.targetApp == "all" && !properties.listenBroadcast) {
            return null
        }
        return commandsService.executeLocal(command)
    }

    override fun executeForGroup(command: Command): Future<List<CommandResult>> {
        val ttlMs = command.ttl?.toMillis() ?: 0
        if (ttlMs <= 0 && ttlMs > TimeUnit.MINUTES.toMillis(10)) {
            throw IllegalArgumentException("Illegal ttl for group command: $ttlMs")
        }
        val future = GroupResultFuture()
        commandsForGroup.put(command.id, future)
        if (commandsForGroup.size() > 10_000) {
            log.warn { "CommandsForGroup size is too bit. Potentially memory leak. Size: " + commandsForGroup.size() }
        }
        try {
            rabbitContext.sendCommand(command)
        } catch (e: Exception) {
            commandsForGroup.remove(command.id)
            throw e
        }
        timer.schedule(ttlMs) {
            future.flushResults()
        }
        return future
    }

    override fun execute(command: Command): Future<CommandResult> {
        val future = CompletableFuture<CommandResult>()
        commands.put(command.id, future)
        if (commands.size() > 10_000) {
            log.warn { "Commands size is too big. Potentially memory leak. Size: " + commands.size() }
        }
        try {
            rabbitContext.sendCommand(command)
        } catch (e: Exception) {
            commands.remove(command.id)
            throw e
        }
        return future
    }
}
