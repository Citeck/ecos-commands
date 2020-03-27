package ru.citeck.ecos.commands.context

import java.util.concurrent.Callable

interface CommandCtxManager {

    fun getCurrentUser() : String

    fun getCurrentTenant() : String

    fun getSourceAppName() : String

    fun getSourceAppInstanceId() : String

    fun <T> runWith(user: String = "",
                    tenant: String = "",
                    appName: String = "",
                    appInstanceId: String = "",
                    action: Callable<T>) : T

}
