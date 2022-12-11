package ru.citeck.ecos.commands.rabbit.test

import com.rabbitmq.client.ConnectionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import kotlin.concurrent.thread

@Disabled("works only with real rabbitmq")
class CommandsStressTest {

    companion object {
        const val APP_0 = "app-0"
        const val APP_1 = "app-1"
    }

    val elements = ArrayList<DataValue>()

    lateinit var app0: TestApp
    lateinit var app1: TestApp

    @BeforeEach
    fun beforeEach() {
        val createApp: (String) -> TestApp = { name: String ->

            val factory = ConnectionFactory()

            factory.host = "localhost"
            factory.port = 5672
            factory.username = "admin"
            factory.password = "admin"
            val rabbitMqConn = RabbitMqConn(factory, initSleepMs = 0L)

            TestApp(name, rabbitMqConn)
        }

        app0 = createApp(APP_0)
        app1 = createApp(APP_1)

        app0.commandsService.addExecutor(AddElementExecutor())
        app1.commandsService.addExecutor(RedirectExecutor())

        elements.clear()
    }

    @Test
    fun test() {
        val total = 100
        (0 until total).map {
            thread {
                println("COMMAND started: $it")
                app0.commandsService.executeSync {
                    withBody(RedirectCommand(DataValue.create(it)))
                    withTargetApp(APP_1)
                }.throwPrimaryErrorIfNotNull()
                println("COMMAND completed: $it")
            }
        }.forEach {
            it.join()
        }
        assertEquals(total, elements.size)
    }

    inner class RedirectExecutor : CommandExecutor<RedirectCommand> {

        override fun execute(command: RedirectCommand): Any {
            println("Redirect COMMAND started: ${command.element}")
            val commResult = app1.commandsService.executeSync {
                this.body = AddElementCommand(command.element)
                this.targetApp = APP_0
            }
            commResult.throwPrimaryErrorIfNotNull()
            println("Redirect COMMAND completed: ${command.element}")
            return commResult.result
        }
    }

    inner class AddElementExecutor : CommandExecutor<AddElementCommand> {

        override fun execute(command: AddElementCommand): Any {
            println("Add element COMMAND started: ${command.element}")
            Thread.sleep(500)
            println("add " + command.element)
            elements.add(command.element)
            println("Add element COMMAND completed: ${command.element}")
            return command
        }
    }

    @CommandType("redirect")
    class RedirectCommand(val element: DataValue)

    @CommandType("add-element")
    class AddElementCommand(val element: DataValue)
}
