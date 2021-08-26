package ru.citeck.ecos.commands.context

class SimpleCommandCtxController : CommandCtxController {

    override fun setCurrentUser(user: String) : String = user

    override fun getCurrentUser() : String = ""

    override fun setCurrentTenant(tenant: String) : String = tenant

    override fun getCurrentTenant() : String = ""

}
