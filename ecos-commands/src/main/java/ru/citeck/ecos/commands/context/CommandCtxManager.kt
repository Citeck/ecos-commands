package ru.citeck.ecos.commands.context

import java.util.concurrent.Callable

object CommandCtxManager {

    var controller: CommandCtxController? = null

    private val currentUser = ThreadLocal<String>()
    private val currentTenant = ThreadLocal<String>()

    private var isInContext = ThreadLocal<Boolean>()

    init {
        isInContext.set(false)
    }

    fun getCurrentUser() : String {
        return if (isInContext.get()) {
            currentUser.get()
        } else {
            controller?.getCurrentUser() ?: ""
        }
    }

    fun getCurrentTenant() : String {
        return if (isInContext.get()) {
            currentTenant.get()
        } else {
            controller?.getCurrentTenant() ?: ""
        }
    }

    fun <T> runWith(user: String, tenant: String, callable: Callable<T>) : T {

        val userBefore = controller?.getCurrentUser() ?: ""
        val tenantBefore = controller?.getCurrentTenant() ?: ""

        return try {

            isInContext.set(true)

            currentUser.set(controller?.setCurrentUser(user) ?: user)
            currentUser.set(controller?.setCurrentTenant(tenant) ?: tenant)

            callable.call()

        } finally {

            isInContext.set(false)

            currentUser.set(userBefore)
            currentTenant.set(tenantBefore)

            controller?.setCurrentUser(userBefore)
            controller?.setCurrentTenant(tenantBefore)
        }
    }
}
