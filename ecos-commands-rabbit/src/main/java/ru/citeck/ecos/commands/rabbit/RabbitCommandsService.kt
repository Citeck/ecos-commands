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
import java.util.concurrent.*
import java.util.function.Consumer
import kotlin.concurrent.schedule

class RabbitCommandsService(
    private val factory: CommandsServiceFactory,
    private val rabbitConnection: RabbitMqConn
) : RemoteCommandsService {

    companion object {
        val log = KotlinLogging.logger {}
    }

    private var contextToSendCommands: RabbitContext? = null
    private var initCommandsQueue = ConcurrentLinkedQueue<InitCommandItem>()

    private val allContexts = CopyOnWriteArrayList<RabbitContext>()

    private val commandsService: CommandsService = factory.commandsService
    private val properties = factory.properties

    private val commands = WeakValuesMap<String, CommandResultFuture>()
    private val commandsForGroup = WeakValuesMap<String, GroupCommandResultFuture>()

    private val timer = Timer("RabbitCommandsTimer", false)

    private val validTargetApps = setOf(
        properties.appName,
        CommandUtils.getTargetAppByAppInstanceId(properties.appInstanceId),
        "all"
    )

    init {
        addNewContext { rabbitCtx ->

            contextToSendCommands = rabbitCtx
            val futures = mutableListOf<CompletableFuture<Boolean>>()

            var commandItem = initCommandsQueue.poll()
            while (commandItem != null) {
                try {
                    val localItem = commandItem
                    futures.add(executeImpl(rabbitCtx, commandItem.command).thenApplyAsync { res ->
                        localItem.resultFuture.complete(res)
                    })
                } catch (e: Exception) {
                    log.error(e) { "Init command can't be executed: ${commandItem.command}" }
                }
                commandItem = initCommandsQueue.poll()
            }
            try {
                CompletableFuture.allOf(*futures.toTypedArray()).get(1, TimeUnit.MINUTES)
            } catch (e: Exception) {
                log.error(e) { "Error while init commands result waiting" }
            }
        }
        repeat(factory.properties.concurrentCommandConsumers - 1) {
            addNewContext {}
        }
    }

    private fun addNewContext(action: (RabbitContext) -> Unit) {
        rabbitConnection.doWithNewChannel(Consumer { channel ->
            val context = RabbitContext(
                channel,
                { onCommandReceived(it) },
                { onResultReceived(it) },
                factory.properties
            )
            allContexts.add(context)
            action.invoke(context)
        }, properties?.channelQos)
    }

    private fun onResultReceived(result: CommandResult) {
        val com = commands.get(result.command.id)
        if (com != null) {
            commands.get(result.command.id)?.complete(result)
        } else {
            commandsForGroup.get(result.command.id)?.results?.add(result)
        }
    }

    private fun onCommandReceived(command: Command) : CommandResult? {

        if (!validTargetApps.contains(command.targetApp)) {
            throw RuntimeException("Incorrect target app name '${command.targetApp}'. " +
                "Expected one of $validTargetApps")
        }
        if (command.targetApp == "all"
                && (!properties.listenBroadcast || !commandsService.containsExecutor(command.type))) {

            return null
        }
        return commandsService.executeLocal(command)
    }

    override fun executeForGroup(command: Command): Future<List<CommandResult>> {

        val ctxToSendCommands = contextToSendCommands ?: return CompletableFuture.completedFuture(emptyList())

        val ttlMs = command.ttl?.toMillis() ?: 0
        if (ttlMs <= 0 && ttlMs > TimeUnit.MINUTES.toMillis(10)) {
            throw IllegalArgumentException("Illegal ttl for group command: $ttlMs")
        }
        val future = GroupCommandResultFuture(ttlMs)
        commandsForGroup.put(command.id, future)
        if (commandsForGroup.size() > 10_000) {
            log.warn { "CommandsForGroup size is too bit. Potentially memory leak. Size: " + commandsForGroup.size() }
        }
        try {
            ctxToSendCommands.sendCommand(command)
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
        val ctxToSendCommands = contextToSendCommands
        return if (ctxToSendCommands == null) {
            val ttlMs = command.ttl?.toMillis()
            val resultFuture = CommandResultFuture(ttlMs)
            initCommandsQueue.add(InitCommandItem(command, resultFuture))
            resultFuture
        } else {
            executeImpl(ctxToSendCommands, command)
        }
    }

    private fun executeImpl(ctxToSendCommands: RabbitContext, command: Command): CompletableFuture<CommandResult> {
        val ttlMs = command.ttl?.toMillis()
        val future = CommandResultFuture(ttlMs)
        commands.put(command.id, future)
        if (commands.size() > 10_000) {
            log.warn { "Commands size is too big. Potentially memory leak. Size: " + commands.size() }
        }
        try {
            ctxToSendCommands.sendCommand(command)
        } catch (e: Exception) {
            commands.remove(command.id)
            throw e
        }
        return future
    }

    override fun dispose() {
        allContexts.forEach { it.close() }
        allContexts.clear()
    }

    private class InitCommandItem(
        val command: Command,
        val resultFuture: CompletableFuture<CommandResult>
    )
}
