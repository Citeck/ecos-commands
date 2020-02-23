package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Delivery
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.utils.EcomObjUtils
import ru.citeck.ecos.commands.utils.ErrorUtils
import ru.citeck.ecos.commons.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap
import kotlin.reflect.KClass

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

        private const val SYSTEM_BYTES_COUNT = 16
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

    fun sendCommand(command: CommandDto, config: CommandConfig) {

        val commandData = EcomObjUtils.toBytes(command)
        val comQueue = COM_QUEUE.format(command.targetApp)

        if (!config.ttl.isZero) {
            publishMsg(
                comQueue,
                true,
                commandData.bytes,
                commandData.dataType,
                AMQP.BasicProperties.Builder()
                    .expiration(config.ttl.toMillis().toString())
                    .build()
            )
        } else {
            publishMsg(comQueue, true, commandData.bytes, commandData.dataType)
        }
    }

    private fun toErrorQueue(message: Delivery, reason: Exception) {

        log.error("Error", reason)

        val errDto = ErrorUtils.convertException(reason)
        val headers = HashMap(message.properties.headers)
        headers["ECOS_ERR"] = Json.mapper.toString(errDto)

        val props = message.properties.builder().headers(headers).build()
        publishMsg(appErrQueue, true, message.body, null, props)
    }

    private fun handleResultMqMessage(message: Delivery) {
        onResult.invoke(parseMsg(message.body, CommandResultDto::class))
    }

    private fun handleCommandMqMessage(message: Delivery) {

        val command = parseMsg(message.body, CommandDto::class)
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


        val bytesToSend = if (dataType != null) {
            val bytes = ByteArray(body.size + SYSTEM_BYTES_COUNT)
            System.arraycopy(body, 0, bytes, SYSTEM_BYTES_COUNT, body.size)
            bytes[0] = dataType.ordinal.toByte()
            bytes
        } else {
            body
        }

        if (!declaredQueues.contains(queue)) {
            declareQueue(queue, durable)
        }

        channel.basicPublish(COM_EXCHANGE, queue, props, bytesToSend)
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

    private fun <T: Any> parseMsg(data: ByteArray, type: KClass<T>) : T {

        val content = ByteArray(data.size - SYSTEM_BYTES_COUNT)
        System.arraycopy(data, SYSTEM_BYTES_COUNT, content, 0, content.size)

        val dataType = EcomObjUtils.DataType.values()[data[0].toInt()]

        return EcomObjUtils.fromBytes(content, dataType, type)
    }
}
