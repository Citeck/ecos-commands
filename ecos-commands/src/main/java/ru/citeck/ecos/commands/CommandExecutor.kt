package ru.citeck.ecos.commands

interface CommandExecutor<T : Any?> {

    fun execute(command: T) : String

    fun getType() : String
}
