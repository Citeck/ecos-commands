package ru.citeck.ecos.commands.transaction

import java.util.concurrent.Callable

class SimpleTxnManager : TransactionManager {
    override fun <T> doInTransaction(action: Callable<T>): T {
        return action.call()
    }
}
