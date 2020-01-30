package ru.citeck.ecos.commands.transaction

import java.util.concurrent.Callable

interface TransactionManager {

    fun <T> doInTransaction(action: Callable<T>) : T
}
