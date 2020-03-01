package ru.citeck.ecos.commands.rabbit.test

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import ecos.com.fasterxml.jackson210.databind.node.NullNode
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.*
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RabbitTest {

    companion object {

        const val APP_0_NAME = "app_0_name"
        const val APP_0_ID = "app_0_id"
        const val APP_1_NAME = "app_1_name"
        const val APP_1_ID = "app_1_id"

        val BYTES_RES = ByteArray(10) { i -> i.toByte() }

        private const val ADD_NEW_ELEM_TYPE = "add_new_element"
        private const val ADD_NEW_ELEM_TYPE2 = "add_new_element2"
        private const val EX_TEST_ELEM = "EX_TEST"
        private const val EX_TEST_MSG = "EX_TEST TEST MSG"
    }

    val elements = ArrayList<String>()

    @Test
    fun test() {

        val factory: ConnectionFactory = MockConnectionFactory()
        factory.host = "localhost"
        factory.username = "admin"
        factory.password = "admin"
        val connection = factory.newConnection()
        val channel = connection.createChannel()

        val app0 = App0(channel)
        val app1 = App1(channel)

        app1.commandsService.addExecutor(AddElementExecutor())
        app1.commandsService.addExecutor(AddElementExecutor2())

        elements.clear()

        val testElem = "test-elem"
        val command = AddElementCommand(testElem)

        val resFuture = app0.commandsService.execute {
            targetApp = APP_1_NAME
            body = command
        }
        val result = resFuture.get()
        val resultObj = result.getResultData(CommandAddResult::class.java)

        assertEquals(testElem, resultObj!!.value)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, elements.size)
        assertEquals(testElem, elements[0])

        val commandFromResult = result.getCommandData(AddElementCommand::class.java)
        assertEquals(commandFromResult, command)

        val exRes = app0.commandsService.execute {
            targetApp = APP_1_NAME
            body = AddElementCommand(EX_TEST_ELEM)
        }.get(1, TimeUnit.SECONDS)

        assertEquals(1, exRes.errors.size)
        assertEquals(EX_TEST_MSG, exRes.errors[0].message)
        assertEquals("RuntimeException", exRes.errors[0].type)
        assertEquals(NullNode.instance, exRes.result)

        app0.commandsService.execute {
            targetApp = "unknown"
            body = command
        }

        val testElem2 = "test-elem2"
        val command2 = AddElementCommand2(testElem2)

        val result2 = app0.commandsService.executeSync {
            targetApp = APP_1_NAME
            body = command2
        }
        val resultObj2 = result2.getResultData(ByteArray::class.java)
        assertTrue(BYTES_RES.contentEquals(resultObj2!!))
    }

    inner class AddElementExecutor : CommandExecutor<AddElementCommand> {

        override fun execute(command: AddElementCommand) : Any {
            if (command.element == EX_TEST_ELEM) {
                throw RuntimeException(EX_TEST_MSG)
            }
            elements.add(command.element)
            return CommandAddResult(command.element)
        }
    }

    inner class AddElementExecutor2 : CommandExecutor<AddElementCommand2> {

        override fun execute(command: AddElementCommand2) : Any {
            elements.add(command.element)
            return BYTES_RES
        }
    }

    data class CommandAddResult(
        val value: String
    )

    @CommandType(ADD_NEW_ELEM_TYPE)
    data class AddElementCommand(
        val element: String
    )

    @CommandType(ADD_NEW_ELEM_TYPE2)
    data class AddElementCommand2(
        val element: String
    )

    class App0(private val channel: Channel) : CommandsServiceFactory() {

        init {
            remoteCommandsService
        }

        override fun createProperties(): CommandsProperties {
            val props = CommandsProperties()
            props.appName = APP_0_NAME
            props.appInstanceId = APP_0_ID
            return props
        }

        override fun createRemoteCommandsService(): RemoteCommandsService? {
            return RabbitCommandsService(this, channel)
        }
    }

    class App1(private val channel: Channel) : CommandsServiceFactory() {

        init {
            remoteCommandsService
        }

        override fun createProperties(): CommandsProperties {
            val props = CommandsProperties()
            props.appName = APP_1_NAME
            props.appInstanceId = APP_1_ID
            return props
        }

        override fun createRemoteCommandsService(): RemoteCommandsService? {
            return RabbitCommandsService(this, channel)
        }
    }
}
