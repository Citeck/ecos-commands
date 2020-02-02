package ru.citeck.ecos.commands.rabbit.test

import com.fasterxml.jackson.databind.node.NullNode
import com.github.fridujo.rabbitmq.mock.MockConnectionFactory
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.*
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

        private const val ADD_NEW_ELEM_TYPE = "add_new_element"
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

        elements.clear()

        val testElem = "test-elem"
        val command = AddElementCommand(testElem)

        val resFuture = app0.commandsService.execute(
            targetApp = APP_1_NAME,
            type = ADD_NEW_ELEM_TYPE,
            data = command
        )
        val result = resFuture.get()
        val resultObj = result.getResultData(CommandAddResult::class.java)

        assertEquals(testElem, resultObj!!.value)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, elements.size)
        assertEquals(testElem, elements[0])

        val commandFromResult = result.getCommandData(AddElementCommand::class.java)
        assertEquals(commandFromResult, command)

        val exRes = app0.commandsService.execute(
            targetApp = APP_1_NAME,
            type = ADD_NEW_ELEM_TYPE,
            data = AddElementCommand(EX_TEST_ELEM)
        ).get(1, TimeUnit.SECONDS)

        assertEquals(1, exRes.errors.size)
        assertEquals(EX_TEST_MSG, exRes.errors[0].message)
        assertEquals("RuntimeException", exRes.errors[0].type)
        assertEquals(NullNode.getInstance(), exRes.result)

        app0.commandsService.execute(
            targetApp = "unknown",
            type = ADD_NEW_ELEM_TYPE,
            data = command
        ).get()
    }

    inner class AddElementExecutor : CommandExecutor<AddElementCommand> {

        override fun execute(command: AddElementCommand) : Any {
            if (command.element == EX_TEST_ELEM) {
                throw RuntimeException(EX_TEST_MSG)
            }
            elements.add(command.element)
            return CommandAddResult(command.element)
        }

        override fun getType(): String {
            return ADD_NEW_ELEM_TYPE
        }
    }

    data class CommandAddResult(
        val value: String
    )

    data class AddElementCommand(
        val element: String
    )

    class App0(private val channel: Channel) : CommandsServiceFactory() {

        init {
            remoteCommandsService
        }

        override fun creatProperties(): CommandsProperties {
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

        override fun creatProperties(): CommandsProperties {
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
