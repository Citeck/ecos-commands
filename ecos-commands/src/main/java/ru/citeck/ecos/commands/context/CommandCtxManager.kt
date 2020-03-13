package ru.citeck.ecos.commands.context

import java.util.concurrent.Callable

object CommandCtxManager {

    @JvmStatic
    var controller: CommandCtxController? = null

    private val context = ThreadLocal.withInitial { CtxState() }
    private var isInContext = ThreadLocal.withInitial { false }

    @JvmStatic
    fun getCurrentUser() : String {
        return if (isInContext.get()) {
            context.get().currentUser
        } else {
            controller?.getCurrentUser() ?: ""
        }
    }

    @JvmStatic
    fun getCurrentTenant() : String {
        return if (isInContext.get()) {
            context.get().currentTenant
        } else {
            controller?.getCurrentTenant() ?: ""
        }
    }

    @JvmStatic
    fun getSourceAppName() : String {
        return context.get().appName
    }

    @JvmStatic
    fun getSourceAppInstanceId() : String {
        return context.get().appInstanceId
    }

    @JvmStatic
    fun <T> runWith(user: String = "",
                    tenant: String = "",
                    appName: String = "",
                    appInstanceId: String = "",
                    action: Callable<T>) : T {

        val userBefore = controller?.getCurrentUser() ?: ""
        val tenantBefore = controller?.getCurrentTenant() ?: ""

        val ctx = context.get()

        val appNameBefore = ctx.appName
        val appInstanceIdBefore = ctx.appInstanceId

        return try {

            isInContext.set(true)

            ctx.currentUser = controller?.setCurrentUser(user) ?: user
            ctx.currentTenant = controller?.setCurrentTenant(tenant) ?: tenant
            ctx.appName = appName
            ctx.appInstanceId = appInstanceId

            action.call()

        } finally {

            isInContext.set(false)

            ctx.currentUser = userBefore
            ctx.currentTenant = tenantBefore
            ctx.appName = appNameBefore
            ctx.appInstanceId = appInstanceIdBefore

            controller?.setCurrentUser(userBefore)
            controller?.setCurrentTenant(tenantBefore)
        }
    }

    data class CtxState(
        var currentUser: String = "",
        var currentTenant: String = "",
        var appName: String = "",
        var appInstanceId: String = ""
    )
}
