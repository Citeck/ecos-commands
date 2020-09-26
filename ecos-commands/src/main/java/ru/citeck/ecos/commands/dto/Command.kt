package ru.citeck.ecos.commands.dto

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore
import ecos.com.fasterxml.jackson210.databind.JsonNode
import ecos.com.fasterxml.jackson210.databind.node.NullNode
import ru.citeck.ecos.commands.TransactionType
import java.time.Duration

data class Command(

    val id: String,
    val tenant: String,
    val time: Long,
    val targetApp: String,
    val user: String,

    val sourceApp: String,
    val sourceAppId: String,

    val type: String,
    val body: JsonNode = NullNode.instance,

    val transaction: TransactionType = TransactionType.REQUIRED,

    @JsonIgnore
    val ttl: Duration?
)
