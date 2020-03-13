package ru.citeck.ecos.commands

import mu.KotlinLogging
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commands.context.CommandCtxManager
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.dto.CommandError
import ru.citeck.ecos.commands.exceptions.ExecutorNotFound
import ru.citeck.ecos.commands.utils.CommandErrorUtils
import ru.citeck.ecos.commons.json.Json
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

private fun needCommandType(command: Any?) : String {
    if (command == null) {
        throw RuntimeException("Command type is undefined for null body")
    }
    return getCommandType(command) ?:
        throw RuntimeException("Command type is undefined for type ${command::class}. See CommandType annotation")
}

private fun getCommandType(command: Any?) : String? {
    if (command == null) {
        return null
    }
    return (command::class.findAnnotation<CommandType>())?.value
}

class CommandsService(factory: CommandsServiceFactory) {

    companion object {
        val log = KotlinLogging.logger {}
    }

    private val props = factory.properties
    private val remote by lazy { factory.remoteCommandsService }
    private val txnManager = factory.transactionManager

    private val executors = ConcurrentHashMap<String, ExecutorInfo>()

    fun executeSync(command: Any) : CommandResult {
        return executeSync {
            body = Json.mapper.toJson(command)
            type = needCommandType(command)
        }
    }

    fun executeForGroupSync(command: Any) : List<CommandResult> {
        return executeForGroupSync {
            body = Json.mapper.toJson(command)
            type = needCommandType(command)
        }
    }

    fun executeSync(block: CommandBuilder.() -> Unit) : CommandResult {
        return execute(block).get(props.commandTimeoutMs, TimeUnit.MILLISECONDS)
    }

    fun executeForGroupSync(block: CommandBuilder.() -> Unit) : List<CommandResult> {
        return executeForGroup(block).get(props.commandTimeoutMs, TimeUnit.MILLISECONDS)
    }

    fun execute(command: Any) : Future<CommandResult> {
        return execute {
            body = Json.mapper.toJson(command)
            type = needCommandType(command)
        }
    }

    fun executeForGroup(command: Any) : Future<List<CommandResult>> {
        return executeForGroup {
            body = Json.mapper.toJson(command)
            type = needCommandType(command)
        }
    }

    fun execute(block: CommandBuilder.() -> Unit) : Future<CommandResult> {
        val (command, config) = CommandBuilder(props).apply(block).build()
        return execute(command, config)
    }

    fun executeForGroup(block: CommandBuilder.() -> Unit) : Future<List<CommandResult>> {
        val builder = CommandBuilder(props)
        builder.ttl = Duration.ofSeconds(2)
        val (command, config) = builder.apply(block).build()
        return executeForGroup(command, config)
    }

    fun executeLocal(command: Command) : CommandResult {

        val executorInfo = executors[command.type] ?: throw ExecutorNotFound(command.type)

        var executorCommand: Any? = null
        if (executorInfo.commandType.classifier !== Any::class) {
            @Suppress("UNCHECKED_CAST")
            val commandClass = executorInfo.commandType.classifier as KClass<Any>
            executorCommand = Json.mapper.convert(command.body, commandClass.java)
        }

        val started = Instant.now()

        val errors = ArrayList<CommandError>()

        val resultObj : Any? = try {
            txnManager.doInTransaction(Callable {
                CommandCtxManager.runWith(
                    user = command.user,
                    tenant = command.tenant,
                    appName = command.sourceApp,
                    appInstanceId = command.sourceAppId,
                    action = Callable { executorInfo.executor.execute(executorCommand) }
                )
            })
        } catch (e : Exception) {
            log.error("Command execution error", e)
            errors.add(CommandErrorUtils.convertException(e))
            null
        }

        return CommandResult(
            id = UUID.randomUUID().toString(),
            started = started.toEpochMilli(),
            completed = Instant.now().toEpochMilli(),
            command = command,
            result = Json.mapper.toJson(resultObj),
            errors = errors,
            appName = props.appName,
            appInstanceId = props.appInstanceId
        )
    }

    fun executeForGroup(command: Command, config: CommandConfig) : Future<List<CommandResult>> {
        val future = remote.executeForGroup(command, config)
        return CompletableFuture.supplyAsync { future.get(props.commandTimeoutMs, TimeUnit.MILLISECONDS) }
    }

    fun execute(command: Command, config: CommandConfig) : Future<CommandResult> {

        return if (command.targetApp == props.appName) {

            CompletableFuture.completedFuture(executeLocal(command))

        } else {

            val future = remote.execute(command, config)
            CompletableFuture.supplyAsync { future.get(props.commandTimeoutMs, TimeUnit.MILLISECONDS) }
        }
    }

    fun <T : Any?> addExecutor(executor: CommandExecutor<T>) {

        val type = executor::class.supertypes.filter {
            it.classifier == CommandExecutor::class
        }.map {
            it.arguments[0].type
        }.first()!!

        @Suppress("UNCHECKED_CAST")
        executor as CommandExecutor<Any?>

        val comType = (type.classifier as? KClass<*>)?.findAnnotation<CommandType>()?.value
        if (comType?.isNotBlank() == true) {
            executors[comType] = ExecutorInfo(executor, type)
        } else {
            log.warn { "Command type is undefined. Please add CommandType annotation to CommandDto. " +
                "Command executor will be ignored: ${executor::class.qualifiedName}" }
        }
    }

    data class ExecutorInfo (
        val executor: CommandExecutor<Any?>,
        val commandType: KType
    )

    class CommandBuilder(props: CommandsProperties) {

        var id: String = UUID.randomUUID().toString()
        var tenant: String = ""
        var time: Instant = Instant.now()
        var user: String = CommandCtxManager.getCurrentUser()

        var sourceApp: String = props.appName
        var sourceAppId: String = props.appInstanceId
        var transaction: TransactionType = TransactionType.REQUIRED
        var ttl: Duration = Duration.ofMillis(props.commandTimeoutMs)

        var targetApp: String = props.appName
        var type: String? = null
        var body: Any? = null

        fun build() : Pair<Command, CommandConfig> {
            return Pair(Command(
                id = id,
                tenant = tenant,
                time = time.toEpochMilli(),
                user = user,
                sourceApp = sourceApp,
                sourceAppId = sourceAppId,
                transaction = transaction,
                targetApp = targetApp,
                type = type ?: needCommandType(body),
                body = Json.mapper.toJson(body)
            ), CommandConfig(
                ttl = ttl
            ))
        }
    }
}
