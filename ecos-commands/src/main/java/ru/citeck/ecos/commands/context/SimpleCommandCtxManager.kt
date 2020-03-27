package ru.citeck.ecos.commands.context

import ru.citeck.ecos.commands.CommandsServiceFactory
import java.util.concurrent.Callable

class SimpleCommandCtxManager(factory: CommandsServiceFactory) : CommandCtxManager {

    private val controller = factory.commandCtxController

    private val context = ThreadLocal.withInitial { CtxState() }
    private var isInContext = ThreadLocal.withInitial { false }

    override fun getCurrentUser() : String {
        return if (isInContext.get()) {
            context.get().currentUser
        } else {
            controller.getCurrentUser()
        }
    }

    override fun getCurrentTenant() : String {
        return if (isInContext.get()) {
            context.get().currentTenant
        } else {
            controller.getCurrentTenant()
        }
    }

    override fun getSourceAppName() : String {
        return context.get().appName
    }

    override fun getSourceAppInstanceId() : String {
        return context.get().appInstanceId
    }

    override fun <T> runWith(user: String,
                             tenant: String,
                             appName: String,
                             appInstanceId: String,
                             action: Callable<T>) : T {

        val userBefore = controller.getCurrentUser()
        val tenantBefore = controller.getCurrentTenant()

        val ctx = context.get()

        val appNameBefore = ctx.appName
        val appInstanceIdBefore = ctx.appInstanceId

        return try {

            isInContext.set(true)

            ctx.currentUser = controller.setCurrentUser(user)
            ctx.currentTenant = controller.setCurrentTenant(tenant)
            ctx.appName = appName
            ctx.appInstanceId = appInstanceId

            action.call()

        } finally {

            isInContext.set(false)

            ctx.currentUser = userBefore
            ctx.currentTenant = tenantBefore
            ctx.appName = appNameBefore
            ctx.appInstanceId = appInstanceIdBefore

            controller.setCurrentUser(userBefore)
            controller.setCurrentTenant(tenantBefore)
        }
    }

    data class CtxState(
        var currentUser: String = "",
        var currentTenant: String = "",
        var appName: String = "",
        var appInstanceId: String = ""
    )

}
