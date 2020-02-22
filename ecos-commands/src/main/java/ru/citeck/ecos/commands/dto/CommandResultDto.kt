package ru.citeck.ecos.commands.dto

data class CommandResultDto(

    val id: String,

    val started: Long,
    val completed: Long,

    val command: CommandDto,

    val result: Any?,
    val errors: List<ErrorDto> = emptyList()
)
