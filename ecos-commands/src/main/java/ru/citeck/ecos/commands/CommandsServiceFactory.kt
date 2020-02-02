package ru.citeck.ecos.commands

import ru.citeck.ecos.commands.context.CommandContextSupplier
import ru.citeck.ecos.commands.context.SimpleContextSupplier
import ru.citeck.ecos.commands.remote.NoopRemoteCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.transaction.SimpleTxnManager
import ru.citeck.ecos.commands.transaction.TransactionManager

open class CommandsServiceFactory {

    val commandsService by lazy { createCommandsService() }
    val remoteCommandsService by lazy { createRemoteCommandsService() }
    val properties by lazy { creatProperties() }
    val transactionManager by lazy { createTransactionManager() }
    val contextSupplier by lazy { createContextSupplier() }

    protected open fun createCommandsService() : CommandsService {
        return CommandsService(this)
    }

    protected open fun creatProperties() : CommandsProperties {
        return CommandsProperties()
    }

    protected open fun createRemoteCommandsService() : RemoteCommandsService? {
        return NoopRemoteCommandsService()
    }

    protected open fun createTransactionManager() : TransactionManager {
        return SimpleTxnManager()
    }

    protected open fun createContextSupplier() : CommandContextSupplier {
        return SimpleContextSupplier()
    }
}
