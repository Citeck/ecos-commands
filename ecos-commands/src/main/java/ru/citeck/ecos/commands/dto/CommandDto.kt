package ru.citeck.ecos.commands.dto

import ru.citeck.ecos.commands.TransactionType

data class CommandDto(
    val id: String,
    val tenant: String,
    val time: Long,
    val targetApp: String,
    val user: String,
    val sourceApp: String,
    val sourceAppId: String,
    val type: String,
    val body: Any?,
    val transaction: TransactionType = TransactionType.REQUIRED
)
