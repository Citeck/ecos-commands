package ru.citeck.ecos.commands.context

interface CommandCtxController {

    fun setCurrentUser(user: String): String

    fun getCurrentUser(): String

    fun setCurrentTenant(tenant: String): String

    fun getCurrentTenant(): String
}
