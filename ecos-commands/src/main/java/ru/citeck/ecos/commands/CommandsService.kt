package ru.citeck.ecos.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.exceptions.ExecutorNotFound
import java.lang.IllegalStateException
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.KType

class CommandsService(factory: CommandsServiceFactory) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(CommandsService::class.java)
    }

    private val props = factory.properties
    private val remote = factory.remoteCommandsService
    private val executors = ConcurrentHashMap<String, ExecutorInfo>()

    private val mapper = ObjectMapper().registerModule(KotlinModule())

    fun execute(targetApp: String = props.appName,
                actor: String = "system",
                type: String,
                data: Any? = null) : Future<CommandResultDto> {

        return execute(CommandDto(
            id = UUID.randomUUID().toString(),
            targetApp = targetApp,
            time = Instant.now(),
            actor = actor,
            source = props.instanceId,
            type = type,
            data = mapper.valueToTree(data)
        ))
    }

    fun execute(command: CommandDto) : Future<CommandResultDto> {

        log.info("Command received: $command")

        if (command.targetApp == props.appName) {

            log.info("Execute command ${command.id} as local")

            val executorInfo = executors[command.type] ?: throw ExecutorNotFound()

            var executerCommand: Any? = null
            if (executorInfo.commandType.classifier !== Any::class) {
                @Suppress("UNCHECKED_CAST")
                val commandClass = executorInfo.commandType.classifier as KClass<Any>
                executerCommand = mapper.treeToValue(command.data, commandClass.java)
            }

            val started = Instant.now()

            val resultMsg = executorInfo.executor.execute(executerCommand)

            val result = CommandResultDto(
                id = UUID.randomUUID().toString(),
                started = started,
                completed = Instant.now(),
                command = command,
                message = resultMsg
            )

            return CompletableFuture.completedFuture(result)

        } else {

            log.info("Execute command ${command.id} as remote")

            if (remote == null) {
                throw IllegalStateException("Remote commands service is not defined!")
            }

            val future = remote.execute(command)
            return CompletableFuture.supplyAsync { future.get(10, TimeUnit.SECONDS) }
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
