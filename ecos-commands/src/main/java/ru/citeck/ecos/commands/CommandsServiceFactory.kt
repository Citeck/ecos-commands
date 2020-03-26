package ru.citeck.ecos.commands

import ru.citeck.ecos.commands.context.CommandCtxController
import ru.citeck.ecos.commands.context.CommandCtxManager
import ru.citeck.ecos.commands.context.SimpleCommandCtxController
import ru.citeck.ecos.commands.context.SimpleCommandCtxManager
import ru.citeck.ecos.commands.remote.NoopRemoteCommandsService
import ru.citeck.ecos.commands.remote.RemoteCommandsService
import ru.citeck.ecos.commands.transaction.SimpleTxnManager
import ru.citeck.ecos.commands.transaction.TransactionManager

open class CommandsServiceFactory {

    val commandsService by lazy { createCommandsService() }
    val remoteCommandsService by lazy { createRemoteCommandsService() }
    val properties by lazy { createProperties() }
    val transactionManager by lazy { createTransactionManager() }
    val commandCtxManager by lazy { createCommandCtxManager() }
    val commandCtxController by lazy { createCommandCtxController() }

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

    protected open fun createCommandCtxManager() : CommandCtxManager {
        return SimpleCommandCtxManager(this)
    }

    protected open fun createCommandCtxController() : CommandCtxController {
        return SimpleCommandCtxController()
    }
}
