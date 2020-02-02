package ru.citeck.ecos.commands.dto

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import java.time.Instant

data class CommandResultDto(

    val id: String,

    val started: Instant,
    val completed: Instant,

    val command: CommandDto,

    val result: JsonNode = MissingNode.getInstance(),
    val errors: List<ErrorDto> = emptyList()
)
