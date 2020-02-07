package ru.citeck.ecos.commands

import ecos.com.fasterxml.jackson210.databind.JsonNode
import ecos.com.fasterxml.jackson210.databind.node.ObjectNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commands.context.CommandCtxManager
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.dto.ErrorDto
import ru.citeck.ecos.commands.exceptions.ExecutorNotFound
import ru.citeck.ecos.commands.utils.EcomObjUtils
import ru.citeck.ecos.commands.utils.ErrorUtils
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

class CommandsService(factory: CommandsServiceFactory) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(CommandsService::class.java)
    }

    private val props = factory.properties
    private val remote by lazy { factory.remoteCommandsService }
    private val txnManager = factory.transactionManager

    private val executors = ConcurrentHashMap<String, ExecutorInfo>()


    fun execute(command: Any, transaction: TransactionType = TransactionType.REQUIRED) : Future<CommandResultDto> {
        return execute(props.appName, command, transaction)
    }

    fun execute(targetApp: String,
                command: Any,
                transaction: TransactionType = TransactionType.REQUIRED) : Future<CommandResultDto> {

        val body = EcomObjUtils.mapper.valueToTree<ObjectNode>(command)
        return execute(targetApp, needCommandType(command), body, transaction)
    }


    fun execute(targetApp: String,
                type: String,
                body: ObjectNode,
                transaction: TransactionType = TransactionType.REQUIRED) : Future<CommandResultDto> {

        return executeCommand(CommandDto(
            id = UUID.randomUUID().toString(),
            tenant = CommandCtxManager.getCurrentTenant(),
            targetApp = targetApp,
            time = Instant.now(),
            user = CommandCtxManager.getCurrentUser(),
            sourceApp = props.appName,
            sourceAppId = props.appInstanceId,
            type = type,
            body = body,
            transaction = transaction
        ))
    }

    fun executeCommand(command: CommandDto) : Future<CommandResultDto> {

        log.info("Command received: $command")

        if (command.targetApp == props.appName) {

            log.info("Execute command ${command.id} as local")

            val executorInfo = executors[command.type] ?: throw ExecutorNotFound()

            var executorCommand: Any? = null
            if (executorInfo.commandType.classifier !== Any::class) {
                @Suppress("UNCHECKED_CAST")
                val commandClass = executorInfo.commandType.classifier as KClass<Any>
                executorCommand = EcomObjUtils.mapper.treeToValue(command.body, commandClass.java)
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

            val result = CommandResultDto(
                id = UUID.randomUUID().toString(),
                started = started,
                completed = Instant.now(),
                command = command,
                result = EcomObjUtils.mapper.valueToTree<JsonNode>(resultObj),
                errors = errors
            )

            return CompletableFuture.completedFuture(result)

        } else {

            log.info("Execute command ${command.id} as remote")

            if (remote == null) {
                throw IllegalStateException("Remote commands service is not defined!")
            }

            val future = remote!!.execute(command)
            return CompletableFuture.supplyAsync { future.get(props.commandTimeoutMs, TimeUnit.MILLISECONDS) }
        }
    }

    private fun needCommandType(command: Any) : String {
        return getCommandType(command) ?:
            throw RuntimeException("Command type is undefined for type ${command::class}. See CommandType annotation")
    }

    fun getCommandType(command: Any) : String? {
        return (command::class.findAnnotation<CommandType>())?.value
    }

    fun <T : Any?> addExecutor(executor: CommandExecutor<T>) {

        val type = executor::class.supertypes.filter {
            it.classifier == CommandExecutor::class
        }.map {
            it.arguments[0].type
        }.first()!!

        @Suppress("UNCHECKED_CAST")
        executor as CommandExecutor<Any?>

        executors[executor.getType()] = ExecutorInfo(executor, type)
    }

    data class ExecutorInfo (
        val executor: CommandExecutor<Any?>,
        val commandType: KType
    )
}
