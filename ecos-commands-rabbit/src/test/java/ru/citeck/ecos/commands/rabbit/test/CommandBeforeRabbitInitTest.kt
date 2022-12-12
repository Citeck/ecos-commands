package ru.citeck.ecos.commands.rabbit.test

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commons.test.EcosWebAppApiMock
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class CommandBeforeRabbitInitTest {

    @Test
    fun test() {

        val factory = MockConnectionFactory()
        factory.host = "localhost"
        factory.username = "admin"
        factory.password = "admin"

        val createConn: () -> RabbitMqConn = {
            RabbitMqConn(factory, initSleepMs = 1000)
        }

        val services0 = object : CommandsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi? {
                return EcosWebAppApiMock("test0", "test0-" + UUID.randomUUID())
            }

            override fun createRemoteCommandsService(): RemoteCommandsService {
                return RabbitCommandsService(this, createConn())
            }
        }
        services0.remoteCommandsService

        val services1 = object : CommandsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi {
                return EcosWebAppApiMock("test1", "test1-" + UUID.randomUUID())
            }
            override fun createRemoteCommandsService(): RemoteCommandsService {
                return RabbitCommandsService(this, createConn())
            }
        }
        services1.remoteCommandsService

        val answer = "TEST ANSWER"

        services1.commandsService.addExecutor(object : CommandExecutor<TestCommand> {
            override fun execute(command: TestCommand): Any? {
                Thread.sleep(500)
                return answer
            }
        })

        val resultFuture = arrayOf(
            services0.commandsService.execute {
                targetApp = "test1"
                body = TestCommand()
            },
            services0.commandsService.execute {
                targetApp = "test1"
                body = TestCommand()
            },
            services0.commandsService.execute {
                targetApp = "test1"
                body = TestCommand()
            }
        ).map {
            CompletableFuture.supplyAsync { it.get() }
        }

        CompletableFuture.allOf(*resultFuture.toTypedArray()).get(30, TimeUnit.SECONDS)

        assertTrue(resultFuture.all { it.isDone })
        assertTrue(
            resultFuture.all {
                (it as CompletableFuture<CommandResult>)
                    .getNow(null)
                    .getResultAs(String::class.java) == answer
            }
        )
    }

    @CommandType("test-command")
    class TestCommand
}
