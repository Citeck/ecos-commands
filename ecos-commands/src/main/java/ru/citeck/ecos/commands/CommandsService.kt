package ru.citeck.ecos.commands

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

class CommandsService(factory: CommandsServiceFactory) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(CommandsService::class.java)
    }

    private val props = factory.properties
    private val remote by lazy { factory.remoteCommandsService }
    private val txnManager = factory.transactionManager
    private val contextSupplier = factory.contextSupplier

    private val executors = ConcurrentHashMap<String, ExecutorInfo>()

    fun execute(targetApp: String = props.appName,
                user: String = contextSupplier.getCurrentUser(),
                type: String,
                data: Any? = null) : Future<CommandResultDto> {

        return execute(CommandDto(
            id = UUID.randomUUID().toString(),
            tenant = contextSupplier.getCurrentTenant(),
            targetApp = targetApp,
            time = Instant.now(),
            user = user,
            sourceApp = props.appName,
            sourceAppId = props.appInstanceId,
            type = type,
            data = EcomObjUtils.mapper.valueToTree(data)
        ))
    }

    fun execute(command: CommandDto) : Future<CommandResultDto> {

        log.info("Command received: $command")

        if (command.targetApp == props.appName) {

            log.info("Execute command ${command.id} as local")

            val executorInfo = executors[command.type] ?: throw ExecutorNotFound()

            var executorCommand: Any? = null
            if (executorInfo.commandType.classifier !== Any::class) {
                @Suppress("UNCHECKED_CAST")
                val commandClass = executorInfo.commandType.classifier as KClass<Any>
                executorCommand = EcomObjUtils.mapper.treeToValue(command.data, commandClass.java)
            }

            val started = Instant.now()

            val errors = ArrayList<ErrorDto>()

            val resultObj : Any? = try {
                txnManager.doInTransaction(Callable {
                    executorInfo.executor.execute(executorCommand)
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
