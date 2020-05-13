package ru.citeck.ecos.commands.spring

import com.rabbitmq.client.ConnectionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class DefaultConnFactoryProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(CommandsConnectionFactoryProvider::class)
    open fun getProvider(mqProps: RabbitMqConnectionProperties) : CommandsConnectionFactoryProvider {
        return Provider(mqProps)
    }

    private class Provider(mqProps: RabbitMqConnectionProperties) : CommandsConnectionFactoryProvider {

        private val connectionFactory: ConnectionFactory?

        init {
            val host = mqProps.host

            if (host != null && host.isNotBlank()) {
                val connectionFactory = ConnectionFactory()
                connectionFactory.isAutomaticRecoveryEnabled = true
                connectionFactory.host = mqProps.host
                connectionFactory.username = mqProps.username
                connectionFactory.password = mqProps.password

                this.connectionFactory = connectionFactory
            } else {
                this.connectionFactory = null
            }
        }

        override fun getConnectionFactory() = connectionFactory
    }
}
