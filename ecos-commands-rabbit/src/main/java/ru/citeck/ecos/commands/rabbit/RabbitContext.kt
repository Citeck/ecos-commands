package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.BuiltinExchangeType
import mu.KotlinLogging
import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.utils.CommandUtils
import ru.citeck.ecos.commands.utils.CommandErrorUtils
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.rabbitmq.RabbitMqChannel
import kotlin.collections.HashMap

class RabbitContext(
    private val channel: RabbitMqChannel,
    private val onCommand: (Command) -> CommandResult?,
    private val onResult: (CommandResult) -> Unit,
    private val properties: CommandsProperties
) {

    companion object {

        private val log = KotlinLogging.logger {}

        private const val COM_EXCHANGE = "ecos-commands"

        private const val COM_QUEUE = "commands.%s.com"
        private const val ERR_QUEUE = "commands.%s.err"
        private const val RES_QUEUE = "commands.%s.res.%s"
    }

    private val appComQueue = COM_QUEUE.format(properties.appName)
    private val appErrQueue = ERR_QUEUE.format(properties.appName)
    private val appResQueue = getResQueueId(properties.appName, properties.appInstanceId)

    private val instanceComQueue = COM_QUEUE.format(
        CommandUtils.getTargetAppByAppInstanceId(properties.appInstanceId)
    )
    private val allComQueueKey = COM_QUEUE.format("all")

    private val comConsumerTag: String
    private val instanceComConsumerTag: String
    private val resConsumerTag: String

    init {

        declareQueue(appComQueue, appComQueue)
        declareQueue(appErrQueue, appErrQueue)
        declareQueue(appResQueue, appResQueue, durable = false)
        declareQueue(instanceComQueue, allComQueueKey, durable = false)
        declareQueue(instanceComQueue, instanceComQueue, durable = false)

        comConsumerTag = addConsumer(appComQueue, Command::class.java) {
            msg,
            _ ->
            handleCommandMqMessage(msg)
        }
        instanceComConsumerTag = addConsumer(instanceComQueue, Command::class.java) {
            msg, _ ->
            handleCommandMqMessage(msg)
        }
        resConsumerTag = addConsumer(appResQueue, CommandResult::class.java) {
            msg, _ ->
            onResult(msg)
        }
    }

    private fun <T : Any> addConsumer(queue: String, type: Class<T>, action: (T, Map<String, Any>) -> Unit): String {
        return channel.addAckedConsumer(queue, type) { msg, headers ->
            try {
                action.invoke(msg.getContent(), headers)
            } catch (e: Exception) {
                toErrorQueue(msg.getContent(), headers, e)
            }
        }
    }

    private fun handleCommandMqMessage(command: Command) {
        val result = onCommand.invoke(command) ?: return
        val resQueue = getResQueueId(command.sourceApp, command.sourceAppId)
        publishMsg(resQueue, result)
    }

    fun sendCommand(command: Command) {
        val comQueue = COM_QUEUE.format(command.targetApp)
        publishMsg(comQueue, command, emptyMap(), command.ttl?.toMillis() ?: properties.commandTimeoutMs)
    }

    private fun toErrorQueue(message: Any, headers: Map<String, Any>, reason: Exception) {

        log.error("Error", reason)

        val errDto = CommandErrorUtils.convertException(reason)
        val newHeaders = HashMap(headers)
        newHeaders["ECOS_ERR"] = Json.mapper.toString(errDto)

        publishMsg(appErrQueue, message, newHeaders, 0L)
    }

    private fun publishMsg(queue: String, body: Any, headers: Map<String, Any> = emptyMap(), ttl: Long = 0L) {
        channel.publishMsg(COM_EXCHANGE, queue, body, headers, ttl)
    }

    private fun exchangeDeclare() {
        channel.declareExchange(COM_EXCHANGE, BuiltinExchangeType.TOPIC, true)
    }

    @Synchronized
    private fun declareQueue(queue: String, routingKey: String, durable: Boolean = true) {
        channel.declareQueue(queue, durable)
        exchangeDeclare()
        channel.queueBind(queue, COM_EXCHANGE, routingKey)
    }

    private fun getResQueueId(appName: String, appId: String): String {
        return RES_QUEUE.format(appName, appId)
    }

    fun close() {
        channel.close()
    }
}
