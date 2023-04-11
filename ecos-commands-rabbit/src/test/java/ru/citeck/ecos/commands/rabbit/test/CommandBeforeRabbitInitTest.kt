package ru.citeck.ecos.commands.rabbit.test

import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import ru.citeck.ecos.rabbitmq.test.EcosRabbitMqTest
import ru.citeck.ecos.test.commons.EcosWebAppApiMock
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertTrue

class CommandBeforeRabbitInitTest {

    @Test
    fun test() {

        val createConn: () -> RabbitMqConn = {
            EcosRabbitMqTest.createConnection()
        }

        val services0WhenAppReadyActions = mutableListOf<() -> Unit>()
        val services0 = object : CommandsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi {
                return object : EcosWebAppApiMock("test-before-init-0", "test0-" + UUID.randomUUID()) {
                    override fun doWhenAppReady(order: Float, action: () -> Unit) {
                        if (services0WhenAppReadyActions.isEmpty()) {
                            thread(start = true) {
                                Thread.sleep(5000)
                                services0WhenAppReadyActions.forEach { it.invoke() }
                            }
                        }
                        services0WhenAppReadyActions.add(action)
                    }
                }
            }

            override fun createRemoteCommandsService(): RemoteCommandsService {
                return RabbitCommandsService(this, createConn())
            }
        }
        services0.remoteCommandsService

        val services1 = object : CommandsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi {
                return EcosWebAppApiMock("test-before-init-1", "test1-" + UUID.randomUUID())
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
                targetApp = "test-before-init-1"
                body = TestCommand()
            },
            services0.commandsService.execute {
                targetApp = "test-before-init-1"
                body = TestCommand()
            },
            services0.commandsService.execute {
                targetApp = "test-before-init-1"
                body = TestCommand()
            }
        ).map { it.asPromise() }

        Promises.all(resultFuture).get(Duration.ofSeconds(30))

        assertTrue(resultFuture.all { it.isDone() })
        assertTrue(
            resultFuture.all {
                it.get(Duration.ZERO).getResultAs(String::class.java) == answer
            }
        )
    }

    @CommandType("test-command")
    class TestCommand
}
