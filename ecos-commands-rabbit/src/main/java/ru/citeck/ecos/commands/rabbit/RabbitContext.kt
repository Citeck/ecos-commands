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
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.utils.CommandErrorUtils
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
    private val onCommand: (Command) -> CommandResult,
    private val onResult: (CommandResult) -> Unit,
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

    private val allComQueue = COM_QUEUE.format(properties.appInstanceId)
    private val allComQueueKey = COM_QUEUE.format("all")

    private val comConsumerTag: String
    private val allConsumerTag: String
    private val resConsumerTag: String

    private val declaredQueues: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    init {
        declareQueue(appComQueue, appComQueue)
        declareQueue(appErrQueue, appErrQueue)
        declareQueue(appResQueue, appResQueue, durable = false)
        declareQueue(allComQueue, allComQueueKey, durable = false)

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

        allConsumerTag = channel.basicConsume(
            allComQueue,
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
            { consumerTag: String -> log.info("All com consuming cancelled. Tag: $consumerTag") }
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

    fun sendCommand(command: Command, config: CommandConfig) {

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

        val errDto = CommandErrorUtils.convertException(reason)
        val headers = HashMap(message.properties.headers)
        headers["ECOS_ERR"] = Json.mapper.toString(errDto)

        val props = message.properties.builder()
            .headers(headers)
            .expiration(null)
            .build()

        publishMsg(appErrQueue, true, message.body, props)
    }

    private fun handleResultMqMessage(message: Delivery) {
        onResult(fromMsgBytes(message.body, CommandResult::class))
    }

    private fun handleCommandMqMessage(message: Delivery) {

        val command = fromMsgBytes(message.body, Command::class)
        val result = onCommand.invoke(command)

        val resQueue = getResQueueId(command.sourceApp, command.sourceAppId)

        publishMsg(resQueue, false, toMsgBytes(result))
    }

    private fun publishMsg(queue: String,
                           durable: Boolean,
                           body: ByteArray,
                           props: AMQP.BasicProperties = AMQP.BasicProperties.Builder().build()) {

        if (!declaredQueues.contains(queue)) {
            declareQueue(queue, queue, durable)
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
    private fun declareQueue(queue: String, routingKey: String, durable: Boolean = true) {
        channel.queueDeclare(
            queue,
            durable,
            true,
            !durable,
            null
        )
        try {
            channel.queueBind(queue, COM_EXCHANGE, routingKey)
        } catch (e: Exception) {
            exchangeDeclare()
            channel.queueBind(queue, COM_EXCHANGE, routingKey)
        }
        declaredQueues.add(queue)
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
