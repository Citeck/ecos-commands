package ru.citeck.ecos.commands.spring

import com.rabbitmq.client.ConnectionFactory

interface CommandsConnectionFactoryProvider {

    fun getConnectionFactory() : ConnectionFactory?
}
