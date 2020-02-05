package ru.citeck.ecos.commands.dto

import ecos.com.fasterxml.jackson210.databind.JsonNode
import ecos.com.fasterxml.jackson210.databind.node.NullNode
import java.time.Instant

data class CommandResultDto(

    val id: String,

    val started: Instant,
    val completed: Instant,

    val command: CommandDto,

    val result: JsonNode = NullNode.getInstance(),
    val errors: List<ErrorDto> = emptyList()
)
