package ru.citeck.ecos.commands.dto

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

data class CommandDto(
    val id: String,
    val tenant: String,
    val time: Instant,
    val targetApp: String,
    val user: String,
    val sourceApp: String,
    val sourceAppId: String,
    val type: String,
    val data: ObjectNode = JsonNodeFactory.instance.objectNode()
)
