package ru.citeck.ecos.commands.dto

import ecos.com.fasterxml.jackson210.databind.node.JsonNodeFactory
import ecos.com.fasterxml.jackson210.databind.node.ObjectNode
import ru.citeck.ecos.commands.TransactionType
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
    val body: ObjectNode = JsonNodeFactory.instance.objectNode(),
    val transaction: TransactionType = TransactionType.REQUIRED
)
