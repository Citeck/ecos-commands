package ru.citeck.ecos.commands.dto

data class ErrorDto(
    val type: String,
    val message: String,
    val stackTrace: List<String> = emptyList()
)
