package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Delivery
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.utils.EcomObjUtils
import ru.citeck.ecos.commands.utils.ErrorUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

class RabbitContext(
    private val channel: Channel,
    properties: CommandsProperties,
    private val onCommand: (CommandDto) -> CommandResultDto,
    private val onResult: (CommandResultDto) -> Unit
) {

    companion object {

        private val log = LoggerFactory.getLogger(RabbitContext::class.java)

        private const val COM_EXCHANGE = "ecos-commands"

        private const val COM_QUEUE = "commands.%s.com"
        private const val ERR_QUEUE = "commands.%s.err"
        private const val RES_QUEUE = "commands.%s.res.%s"

        private const val DATA_TYPE_HEADER = "DATA_TYPE"
    }

    private val appComQueue = COM_QUEUE.format(properties.appName)
    private val appErrQueue = ERR_QUEUE.format(properties.appName)
    private val appResQueue = getResQueueId(properties.appName, properties.appInstanceId)

    private val comConsumerTag: String
    private val resConsumerTag: String

    private val declaredQueues: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    init {
        declareQueue(appComQueue)
        declareQueue(appErrQueue)
        declareQueue(appResQueue, durable = false)

        comConsumerTag = channel.basicConsume(
            appComQueue,
            true,
            { _, message: Delivery ->
                run {
                    try {
                        handleCommandMqMessage(message)
                    } catch (e: Exception) {
                        toErrorQueue(message, e)
                    }
                }
            },
            { consumerTag: String -> log.info("Com consuming cancelled. Tag: $consumerTag") }
        )

        resConsumerTag = channel.basicConsume(
            appResQueue,
            true,
            { _, message: Delivery ->
                run {
                    try {
                        handleResultMqMessage(message)
                    } catch (e: Exception) {
                        toErrorQueue(message, e)
                    }
                }
            },
            { consumerTag: String -> log.info("Res consuming cancelled. Tag: $consumerTag") }
        )
    }

    fun sendCommand(command: CommandDto) {

        val commandData = EcomObjUtils.toBytes(command)
        val comQueue = COM_QUEUE.format(command.targetApp)

        publishMsg(comQueue, true, commandData.bytes, commandData.dataType)
    }

    private fun toErrorQueue(message: Delivery, reason: Exception) {

        log.error("Error", reason)

        val errDto = ErrorUtils.convertException(reason)
        val headers = HashMap(message.properties.headers)
        headers["ECOS_ERR"] = EcomObjUtils.mapper.writeValueAsString(errDto)

        val props = message.properties.builder().headers(headers).build()
        publishMsg(appErrQueue, true, message.body, null, props)
    }

    private fun handleResultMqMessage(message: Delivery) {

        val type = message.properties.headers[DATA_TYPE_HEADER] as String
        val result = EcomObjUtils.fromBytes(message.body, EcomObjUtils.DataType.valueOf(type), CommandResultDto::class)
        onResult.invoke(result)
    }

    private fun handleCommandMqMessage(message: Delivery) {

        val type = message.properties.headers[DATA_TYPE_HEADER] as String
        val command = EcomObjUtils.fromBytes(message.body, EcomObjUtils.DataType.valueOf(type), CommandDto::class)
        val result = onCommand.invoke(command)

        val resQueue = getResQueueId(command.sourceApp, command.sourceAppId)
        val data = EcomObjUtils.toBytes(result)

        publishMsg(resQueue, false, data.bytes, data.dataType)
    }

    private fun publishMsg(queue: String,
                           durable: Boolean,
                           body: ByteArray,
                           dataType: EcomObjUtils.DataType? = null,
                           props: AMQP.BasicProperties = AMQP.BasicProperties.Builder().build()) {

        val publishProps = if (dataType != null) {
            val headers = HashMap<String, Any>()
            if (props.headers != null) {
                headers.putAll(props.headers)
            }
            headers[DATA_TYPE_HEADER] = dataType.toString()
            props.builder().headers(headers).build()
        } else {
            props
        }

        if (!declaredQueues.contains(queue)) {
            declareQueue(queue, durable)
        }

        channel.basicPublish(COM_EXCHANGE, queue, publishProps, body)
    }

    private fun exchangeDeclare() {
        channel.exchangeDeclare(
            COM_EXCHANGE,
            BuiltinExchangeType.TOPIC,
            true,
            false,
            false,
            emptyMap()
        )
    }

    @Synchronized
    private fun declareQueue(name: String, durable: Boolean = true) {
        channel.queueDeclare(
            name,
            durable,
            true,
            !durable,
            null
        )
        try {
            channel.queueBind(name, COM_EXCHANGE, name)
        } catch (e: Exception) {
            exchangeDeclare()
            channel.queueBind(name, COM_EXCHANGE, name)
        }
        declaredQueues.add(name)
    }

    private fun getResQueueId(appName: String, appId: String) : String {
        val normInstanceId = appId.replace("[^a-zA-Z_-]", "_")
        return RES_QUEUE.format(appName, normInstanceId)
    }
}
