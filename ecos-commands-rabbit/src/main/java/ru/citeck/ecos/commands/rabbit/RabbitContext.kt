package ru.citeck.ecos.commands.rabbit

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Delivery
import ecos.com.fasterxml.jackson210.databind.node.NullNode
import ecos.com.fasterxml.jackson210.dataformat.cbor.CBORFactory
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.utils.ErrorUtils
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.json.JsonOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.collections.HashMap
import kotlin.reflect.KClass

class RabbitContext(
    private val channel: Channel,
    private val onCommand: (CommandDto) -> CommandResultDto,
    private val onResult: (CommandResultDto) -> Unit,
    properties: CommandsProperties
) {

    companion object {

        private val log = LoggerFactory.getLogger(RabbitContext::class.java)

        private const val COM_EXCHANGE = "ecos-commands"

        private const val COM_QUEUE = "commands.%s.com"
        private const val ERR_QUEUE = "commands.%s.err"
        private const val RES_QUEUE = "commands.%s.res.%s"

        private val msgBodyMapper = Json.newMapper(JsonOptions.create {
           setFactory(CBORFactory())
        })
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

        val msgBody = toMsgBytes(command)
        val comQueue = COM_QUEUE.format(command.targetApp)

        if (!config.ttl.isZero) {
            publishMsg(
                comQueue,
                true,
                msgBody,
                AMQP.BasicProperties.Builder()
                    .expiration(config.ttl.toMillis().toString())
                    .build()
            )
        } else {
            publishMsg(comQueue, true, msgBody)
        }
    }

    private fun toErrorQueue(message: Delivery, reason: Exception) {

        log.error("Error", reason)

        val errDto = ErrorUtils.convertException(reason)
        val headers = HashMap(message.properties.headers)
        headers["ECOS_ERR"] = Json.mapper.toString(errDto)

        val props = message.properties.builder().headers(headers).build()
        publishMsg(appErrQueue, true, message.body, props)
    }

    private fun handleResultMqMessage(message: Delivery) {
        onResult(fromMsgBytes(message.body, CommandResultDto::class))
    }

    private fun handleCommandMqMessage(message: Delivery) {

        val command = fromMsgBytes(message.body, CommandDto::class)
        val result = onCommand.invoke(command)

        val resQueue = getResQueueId(command.sourceApp, command.sourceAppId)

        publishMsg(resQueue, false, toMsgBytes(result))
    }

    private fun publishMsg(queue: String,
                           durable: Boolean,
                           body: ByteArray,
                           props: AMQP.BasicProperties = AMQP.BasicProperties.Builder().build()) {

        if (!declaredQueues.contains(queue)) {
            declareQueue(queue, durable)
        }

        channel.basicPublish(COM_EXCHANGE, queue, props, body)
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

    private fun toMsgBytes(data: Any?) : ByteArray {

        val baos = ByteArrayOutputStream()

        GZIPOutputStream(baos).use {
            if (data != null) {
                msgBodyMapper.write(it, data)
            } else {
                msgBodyMapper.write(it, NullNode.instance)
            }
        }

        return baos.toByteArray()
    }

    private fun <T : Any> fromMsgBytes(bytes: ByteArray, type: KClass<T>) : T {

        val input = GZIPInputStream(ByteArrayInputStream(bytes))
        return msgBodyMapper.read(input, type.java)!!
    }
}
