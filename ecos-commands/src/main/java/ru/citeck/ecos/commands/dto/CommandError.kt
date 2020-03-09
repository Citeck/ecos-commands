package ru.citeck.ecos.commands.dto

data class CommandError(
    val type: String,
    val message: String,
    val stackTrace: List<String> = emptyList()
)
