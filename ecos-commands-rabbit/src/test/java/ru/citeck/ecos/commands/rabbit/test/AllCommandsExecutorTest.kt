package ru.citeck.ecos.commands.rabbit.test

import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.rabbitmq.test.EcosRabbitMqTest
import kotlin.test.assertEquals

class AllCommandsExecutorTest {

    companion object {

        const val APP_0_NAME = "app_0_name"
        const val APP_1_NAME = "app_1_name"
    }

    val elements = ArrayList<Command>()

    @Test
    fun test() {

        val rabbitMqConn = EcosRabbitMqTest.getConnection()

        val app0 = TestApp(APP_0_NAME, rabbitMqConn)
        val app1 = TestApp(APP_1_NAME, rabbitMqConn)

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
}
