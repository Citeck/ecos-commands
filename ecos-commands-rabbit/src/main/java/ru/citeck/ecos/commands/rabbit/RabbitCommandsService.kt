package ru.citeck.ecos.commands.rabbit

import mu.KotlinLogging
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.utils.CommandUtils
import ru.citeck.ecos.commands.utils.WeakValuesMap
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import ru.citeck.ecos.webapp.api.promise.Promise
import java.lang.IllegalArgumentException
import java.util.*
import java.util.concurrent.*
import kotlin.concurrent.thread

class RabbitCommandsService(
    private val factory: CommandsServiceFactory,
    private val rabbitConnection: RabbitMqConn
) : RemoteCommandsService {

    companion object {
        val log = KotlinLogging.logger {}
    }

    @Volatile
    private var contextToSendCommands: RabbitContext? = null
    private var initCommandsQueue = ConcurrentLinkedQueue<InitCommandItem>()

    private val allContexts = CopyOnWriteArrayList<RabbitContext>()

    private val commandsService: CommandsService = factory.commandsService
    private val properties = factory.properties
    private val webappProps = factory.webappProps

    private val commands = WeakValuesMap<String, CompletableFuture<CommandResult>>()
    private val commandsForGroup = WeakValuesMap<String, GroupResultFuture>()

    private val timer = Timer("RabbitCommandsTimer", false)

    private val validTargetApps = setOf(
        webappProps.appName,
        CommandUtils.getTargetAppByAppInstanceId(webappProps.appInstanceId),
        "all"
    )

    init {
        addNewContext(RabbitContext.ListenMode.NONE) { rabbitCtx ->

            contextToSendCommands = rabbitCtx
            val futures = mutableListOf<CompletableFuture<Boolean>>()

            var commandItem = initCommandsQueue.poll()
            while (commandItem != null) {
                try {
                    val localItem = commandItem
                    futures.add(
                        executeImpl(rabbitCtx, commandItem.command).thenApplyAsync { res ->
                            localItem.resultFuture.complete(res)
                        }
                    )
                } catch (e: Exception) {
                    log.error(e) { "Init command can't be executed: ${commandItem.command}" }
                }
                commandItem = initCommandsQueue.poll()
            }
            // init action should not block execution
            thread(name = "init-commands-waiting-thread") {
                try {
                    CompletableFuture.allOf(*futures.toTypedArray()).get(1, TimeUnit.MINUTES)
                } catch (e: Exception) {
                    log.error(e) { "Error while init commands result waiting" }
                }
            }
        }
        repeat(factory.properties.concurrentCommandConsumers) {
            addNewContext(RabbitContext.ListenMode.ALL) {}
        }
        // fix deadlock when all command consumers is busy and can't receive results
        addNewContext(RabbitContext.ListenMode.RESULTS) {}
    }

    private fun addNewContext(listenMode: RabbitContext.ListenMode, action: (RabbitContext) -> Unit) {
        val addNewCtxAction = {
            rabbitConnection.doWithNewChannel(
                properties.channelQos
            ) { channel ->
                val context = RabbitContext(
                    channel,
                    { onCommandReceived(it) },
                    { onResultReceived(it) },
                    factory.properties,
                    factory.webappProps,
                    listenMode
                )
                allContexts.add(context)
                action.invoke(context)
            }
        }
        val webAppCtx = factory.getEcosWebAppApi()
        if (webAppCtx != null) {
            webAppCtx.doWhenAppReady {
                addNewCtxAction.invoke()
            }
        } else {
            addNewCtxAction.invoke()
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
                    "Expected one of $validTargetApps"
            )
        }
        val listenBroadcast = properties.listenBroadcast && factory.getEcosWebAppApi()?.isReady() == true

        if (command.targetApp == "all" &&
            (!listenBroadcast || !commandsService.containsExecutor(command.type))
        ) {

            return null
        }
        return AuthContext.runAsSystem {
            commandsService.executeLocal(command)
        }
    }

    override fun executeForGroup(command: Command): Promise<List<CommandResult>> {

        val ctxToSendCommands = contextToSendCommands ?: return Promises.resolve(emptyList())

        val future = GroupResultFuture()

        val ttlMs = command.ttl?.toMillis() ?: 0
        if (ttlMs <= 0 && ttlMs > TimeUnit.MINUTES.toMillis(10)) {
            future.completeExceptionally(IllegalArgumentException("Illegal ttl for group command: $ttlMs"))
            return Promises.create(future)
        }
        commandsForGroup.put(command.id, future)
        if (commandsForGroup.size() > 10_000) {
            log.warn { "CommandsForGroup size is too bit. Potentially memory leak. Size: " + commandsForGroup.size() }
        }
        try {
            ctxToSendCommands.sendCommand(command)
            timer.schedule(
                object : TimerTask() {
                    override fun run() {
                        future.flushResults()
                    }
                },
                ttlMs
            )
        } catch (e: Throwable) {
            commandsForGroup.remove(command.id)
            future.completeExceptionally(e)
        }
        return Promises.create(future)
    }

    override fun execute(command: Command): Promise<CommandResult> {
        val ctxToSendCommands = contextToSendCommands
        val future = if (ctxToSendCommands == null) {
            val resultFuture = CompletableFuture<CommandResult>()
            initCommandsQueue.add(InitCommandItem(command, resultFuture))
            resultFuture
        } else {
            executeImpl(ctxToSendCommands, command)
        }
        return Promises.create(future)
    }

    private fun executeImpl(ctxToSendCommands: RabbitContext, command: Command): CompletableFuture<CommandResult> {
        val future = CompletableFuture<CommandResult>()
        commands.put(command.id, future)
        if (commands.size() > 10_000) {
            log.warn { "Commands size is too big. Potentially memory leak. Size: " + commands.size() }
        }
        try {
            ctxToSendCommands.sendCommand(command)
        } catch (e: Throwable) {
            commands.remove(command.id)
            future.completeExceptionally(e)
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
