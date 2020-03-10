package ru.citeck.ecos.commands.spring

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.rabbitmq.client.ConnectionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
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
    private lateinit var mqProps: RabbitMqConnectionProperties

    @Value("\${ecos.application.name:spring.application.name:}")
    private lateinit var appName: String

    @Value("\${ecos.application.instanceId:eureka.instance.instanceId:}")
    private lateinit var appInstanceId: String

    @Bean
    override fun createCommandsService(): CommandsService {
        return super.createCommandsService()
    }

    @Bean
    @ConditionalOnMissingBean(CommandsProperties::class)
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
    override fun createRemoteCommandsService(): RemoteCommandsService {

        if (mqProps.host?.isBlank() != false) {

            val connectionFactory = ConnectionFactory()
            connectionFactory.isAutomaticRecoveryEnabled = true
            connectionFactory.host = mqProps.host
            connectionFactory.username = mqProps.username
            connectionFactory.password = mqProps.password

            try {
                val connection = connectionFactory.newConnection()
                val channel = connection.createChannel()
                return RabbitCommandsService(this, channel)
            } catch (e: Exception) {
                log.error("Cannot configure connection to RabbitMQ", e)
            }
        }

        log.warn("Rabbit mq host is null. Remote commands will not be available")
        return super.createRemoteCommandsService()
    }

    @Autowired
    fun setProperties(properties: CommandsProperties) {
        this.props = properties
    }

    @Autowired
    fun setMqProperties(props: RabbitMqConnectionProperties) {
        this.mqProps = props
    }
}
