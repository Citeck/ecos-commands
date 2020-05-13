package ru.citeck.ecos.commands.spring

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
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
    private lateinit var connectionProvider: CommandsConnectionFactoryProvider

    @Value("\${spring.application.name:}")
    private lateinit var appName: String

    @Value("\${eureka.instance.instanceId:}")
    private lateinit var appInstanceId: String

    @Bean
    override fun createCommandsService(): CommandsService {
        return super.createCommandsService()
    }

    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        createRemoteCommandsService().init()
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

        val connectionFactory = connectionProvider.getConnectionFactory()
        if (connectionFactory != null) {
            return RabbitCommandsService(this, connectionFactory)
        }
        log.warn("Rabbit mq host is null. Remote commands will not be available")
        return super.createRemoteCommandsService()
    }

    @Autowired
    fun setProperties(properties: CommandsProperties) {
        this.props = properties
    }

    @Autowired
    fun setCommandsConnectionProvider(connectionProvider: CommandsConnectionFactoryProvider) {
        this.connectionProvider = connectionProvider
    }
}
