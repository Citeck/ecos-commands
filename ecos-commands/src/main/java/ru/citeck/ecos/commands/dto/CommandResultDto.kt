package ru.citeck.ecos.commands.dto

import ecos.com.fasterxml.jackson210.databind.JsonNode
import ecos.com.fasterxml.jackson210.databind.node.NullNode

data class CommandResultDto(

    val id: String,

    val started: Long,
    val completed: Long,

    val command: CommandDto,

    val result: JsonNode = NullNode.instance,
    val errors: List<ErrorDto> = emptyList()
)
