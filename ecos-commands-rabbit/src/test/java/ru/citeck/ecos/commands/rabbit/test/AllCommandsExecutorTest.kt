package ru.citeck.ecos.commands.rabbit.test

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory
import com.rabbitmq.client.ConnectionFactory
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import kotlin.test.assertEquals

class AllCommandsExecutorTest {

    companion object {

        const val APP_0_NAME = "app_0_name"
        const val APP_0_ID = "app_0_id"
        const val APP_1_NAME = "app_1_name"
        const val APP_1_ID = "app_1_id"
    }

    val elements = ArrayList<Command>()

    @Test
    fun test() {

        val factory: ConnectionFactory = MockConnectionFactory()
        factory.host = "localhost"
        factory.username = "admin"
        factory.password = "admin"
        val rabbitMqConn = RabbitMqConn(factory)

        val app0 = App0(rabbitMqConn)
        val app1 = App1(rabbitMqConn)

        app0.commandsService.addExecutor(AddElementExecutor())

        val command = app1.commandsService.buildCommand {
            targetApp = APP_0_NAME
            body = "test-test"
            type = "custom-type"
        }
        val commandWithoutTtl = app1.commandsService.buildCommand(command) { ttl = null }

        app1.commandsService.executeSync(command)
        app1.commandsService.executeSync(command)

        val command1 = app1.commandsService.buildCommand {
            targetApp = APP_0_NAME
            body = "test-test"
            type = "other command type"
        }
        val command1WithoutTtl = app1.commandsService.buildCommand(command1) { ttl = null }

        app1.commandsService.executeSync(command1)

        assertEquals(3, elements.size)
        assertEquals(commandWithoutTtl, elements[0])
        assertEquals(commandWithoutTtl, elements[1])
        assertEquals(command1WithoutTtl, elements[2])
    }

    inner class AddElementExecutor : CommandExecutor<Command> {

        override fun execute(command: Command): Any {
            elements.add(command)
            return command
        }
    }

    class App0(private val conntectionFactory: RabbitMqConn) : CommandsServiceFactory() {

        init {
            remoteCommandsService
        }

        override fun createProperties(): CommandsProperties {
            val props = CommandsProperties()
            props.appName = APP_0_NAME
            props.appInstanceId = APP_0_ID
            return props
        }

        override fun createRemoteCommandsService(): RemoteCommandsService {
            return RabbitCommandsService(this, conntectionFactory)
        }
    }

    class App1(private val conntectionFactory: RabbitMqConn) : CommandsServiceFactory() {

        init {
            remoteCommandsService
        }

        override fun createProperties(): CommandsProperties {
            val props = CommandsProperties()
            props.appName = APP_1_NAME
            props.appInstanceId = APP_1_ID
            return props
        }

        override fun createRemoteCommandsService(): RemoteCommandsService {
            return RabbitCommandsService(this, conntectionFactory)
        }
    }
}
