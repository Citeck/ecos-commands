package ru.citeck.ecos.commands.rabbit.test

import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.rabbitmq.RabbitMqConn
import java.util.concurrent.atomic.AtomicLong

class TestApp(val appName: String, private val conntectionFactory: RabbitMqConn) : CommandsServiceFactory() {

    companion object {
        private val idx = AtomicLong()
    }

    init {
        remoteCommandsService
    }

    override fun createProperties(): CommandsProperties {
        val props = CommandsProperties()
        props.appName = appName
        props.appInstanceId = this.appName + ":" + idx.getAndIncrement()
        props.listenBroadcast = false
        return props
    }

    override fun createRemoteCommandsService(): RemoteCommandsService {
        return RabbitCommandsService(this, conntectionFactory)
    }
}
