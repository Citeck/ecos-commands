package ru.citeck.ecos.commands.dto

import java.time.Instant

data class CommandResultDto(

    val id: String,

    val started: Instant,
    val completed: Instant,

    val command: CommandDto,

    val message: String = "",
    val errors: List<ErrorDto> = emptyList()
)
