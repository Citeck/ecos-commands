package ru.citeck.ecos.commands

import ru.citeck.ecos.commands.remote.NoopRemoteCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.transaction.SimpleTxnManager
import ru.citeck.ecos.commands.transaction.TransactionManager

open class CommandsServiceFactory {

    val commandsService by lazy { createCommandsService() }
    val remoteCommandsService by lazy { createRemoteCommandsService() }
    val properties by lazy { createProperties() }
    val transactionManager by lazy { createTransactionManager() }

    protected open fun createCommandsService() : CommandsService {
        return CommandsService(this)
    }

    protected open fun createProperties() : CommandsProperties {
        return CommandsProperties()
    }

    protected open fun createRemoteCommandsService() : RemoteCommandsService {
        return NoopRemoteCommandsService(this)
    }

    protected open fun createTransactionManager() : TransactionManager {
        return SimpleTxnManager()
    }
}
