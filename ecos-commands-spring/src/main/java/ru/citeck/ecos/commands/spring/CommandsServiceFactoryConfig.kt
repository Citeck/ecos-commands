package ru.citeck.ecos.commands.spring

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commands.CommandsProperties
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commands.rabbit.RabbitCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService

@Configuration
open class CommandsServiceFactoryConfig : CommandsServiceFactory() {

    companion object {
        private val log = LoggerFactory.getLogger(CommandsServiceFactoryConfig::class.java)
    }

    private var props = CommandsProperties()
    private var connectionFactory: ConnectionFactory? = null

    @Value("\${ecos.application.name:spring.application.name:}")
    private lateinit var appName: String

    @Value("\${ecos.application.instanceId:eureka.instance.instanceId:}")
    private lateinit var appInstanceId: String

    @Bean
    override fun createCommandsService(): CommandsService {
        return super.createCommandsService()
    }

    override fun createProperties(): CommandsProperties {
        if (props.appName.isBlank()) {
            props.appName = appName
        }
        if (props.appInstanceId.isBlank()) {
            props.appInstanceId = appInstanceId
        }
        return props
    }

    @Bean
    override fun createRemoteCommandsService(): RemoteCommandsService? {

        val connectionFactory = connectionFactory

        if (connectionFactory == null) {
            log.warn("Rabbit connection factory is null. Remote commands will not be available")
            return super.createRemoteCommandsService()
        }
        val connection = connectionFactory.createConnection()
        val channel = connection.createChannel(false)

        return RabbitCommandsService(this, channel)
    }

    @Autowired(required = false)
    fun setConnectionFactory(connectionFactory: ConnectionFactory) {
        this.connectionFactory = connectionFactory
    }

    @Autowired
    fun setProperties(properties: CommandsProperties) {
        this.props = properties
    }
}
