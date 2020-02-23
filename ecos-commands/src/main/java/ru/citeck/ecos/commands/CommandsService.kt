package ru.citeck.ecos.commands

import mu.KotlinLogging
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commands.context.CommandCtxManager
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.dto.ErrorDto
import ru.citeck.ecos.commands.exceptions.ExecutorNotFound
import ru.citeck.ecos.commands.utils.EcomObjUtils
import ru.citeck.ecos.commands.utils.ErrorUtils
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

val log = KotlinLogging.logger {}

private fun needCommandType(command: Any?) : String {
    if (command == null) {
        throw RuntimeException("Command type is undefined for null body")
    }
    return getCommandType(command) ?:
        throw RuntimeException("Command type is undefined for type ${command::class}. See CommandType annotation")
}

fun getCommandType(command: Any?) : String? {
    if (command == null) {
        return null
    }
    return (command::class.findAnnotation<CommandType>())?.value
}

class CommandsService(factory: CommandsServiceFactory) {

    private val props = factory.properties
    private val remote by lazy { factory.remoteCommandsService }
    private val txnManager = factory.transactionManager

    private val executors = ConcurrentHashMap<String, ExecutorInfo>()

    fun executeSync(command: Any) : CommandResultDto {
        return executeSync {
            body = command
            type = needCommandType(command)
        }
    }

    fun executeSync(block: CommandBuilder.() -> Unit) : CommandResultDto {
        return execute(block).get(props.commandTimeoutMs, TimeUnit.MILLISECONDS)
    }

    fun execute(command: Any) : Future<CommandResultDto> {
        return execute {
            body = command
            type = needCommandType(command)
        }
    }

    fun execute(block: CommandBuilder.() -> Unit) : Future<CommandResultDto> {
        val (command, config) = CommandBuilder(props).apply(block).build()
        return execute(command, config)
    }

    fun executeLocal(command: CommandDto) : CommandResultDto {

        log.info("Execute command ${command.id} as local")

        val executorInfo = executors[command.type] ?: throw ExecutorNotFound(command.type)

        var executorCommand: Any? = null
        if (executorInfo.commandType.classifier !== Any::class) {
            @Suppress("UNCHECKED_CAST")
            val commandClass = executorInfo.commandType.classifier as KClass<Any>
            executorCommand = Json.mapper.convert(command.body, commandClass.java)
        }

        val started = Instant.now()

        val errors = ArrayList<ErrorDto>()

        val resultObj : Any? = try {
            txnManager.doInTransaction(Callable {
                CommandCtxManager.runWith(command.user, command.tenant, Callable {
                    executorInfo.executor.execute(executorCommand)
                })
            })
        } catch (e : Exception) {
            log.error("Command execution error", e)
            errors.add(ErrorUtils.convertException(e))
            null
        }

        return CommandResultDto(
            id = UUID.randomUUID().toString(),
            started = started.toEpochMilli(),
            completed = Instant.now().toEpochMilli(),
            command = command,
            result = resultObj,
            errors = errors
        )
    }

    fun execute(command: CommandDto, config: CommandConfig) : Future<CommandResultDto> {

        log.info("Command received: $command")

        return if (command.targetApp == props.appName) {

            CompletableFuture.completedFuture(executeLocal(command))

        } else {

            log.info("Execute command ${command.id} as remote")

            if (remote == null) {
                throw IllegalStateException("Remote commands service is not defined!")
            }

            val future = remote!!.execute(command, config)
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

        fun build() : Pair<CommandDto, CommandConfig> {
            return Pair(CommandDto(
                id = id,
                tenant = tenant,
                time = time.toEpochMilli(),
                user = user,
                sourceApp = sourceApp,
                sourceAppId = sourceAppId,
                transaction = transaction,
                targetApp = targetApp,
                type = type ?: needCommandType(body),
                body = body
            ), CommandConfig(
                ttl = ttl
            ))
        }
    }
}
