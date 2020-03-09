package ru.citeck.ecos.commands.dto

import ecos.com.fasterxml.jackson210.databind.JsonNode
import ecos.com.fasterxml.jackson210.databind.node.NullNode
import ru.citeck.ecos.commons.json.Json

data class CommandResult(

    val id: String,

    val started: Long,
    val completed: Long,

    val command: Command,

    val appName: String,
    val appInstanceId: String,

    val result: JsonNode = NullNode.instance,
    val errors: List<CommandError> = emptyList()
) {

    fun <T : Any> getResultAs(type: Class<T>) : T? {
        return Json.mapper.convert(result, type)
    }

    fun <T : Any> getCommandAs(type: Class<T>) : T? {
        return Json.mapper.convert(command.body, type)
    }
}