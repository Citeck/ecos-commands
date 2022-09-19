package ru.citeck.ecos.commands.rabbit.test

import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commons.test.EcosWebAppContextMock
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import ru.citeck.ecos.webapp.api.context.EcosWebAppContext

class TestApp(val appName: String, private val conntectionFactory: RabbitMqConn) : CommandsServiceFactory() {

    init {
        remoteCommandsService
    }

    override fun getEcosWebAppContext(): EcosWebAppContext {
        return EcosWebAppContextMock(appName)
    }

    override fun createProperties(): CommandsProperties {
        return CommandsProperties.create {
            withListenBroadcast(false)
        }
    }

    override fun createRemoteCommandsService(): RemoteCommandsService {
        return RabbitCommandsService(this, conntectionFactory)
    }
}
