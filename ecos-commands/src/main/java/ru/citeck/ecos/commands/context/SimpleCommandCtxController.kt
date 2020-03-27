package ru.citeck.ecos.commands.context

class SimpleCommandCtxController : CommandCtxController {

    override fun setCurrentUser(user: String) : String = ""

    override fun getCurrentUser() : String = ""

    override fun setCurrentTenant(tenant: String) : String = ""

    override fun getCurrentTenant() : String = ""

}
