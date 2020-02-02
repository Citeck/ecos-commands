package ru.citeck.ecos.commands.context

class SimpleContextSupplier : CommandContextSupplier {

    override fun getCurrentUser(): String = "system"

    override fun getCurrentTenant(): String = ""
}
