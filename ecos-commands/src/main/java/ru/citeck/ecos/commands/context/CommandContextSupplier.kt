package ru.citeck.ecos.commands.context

interface CommandContextSupplier {

    fun getCurrentUser() : String

    fun getCurrentTenant() : String
}
