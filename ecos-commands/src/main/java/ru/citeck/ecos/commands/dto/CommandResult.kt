package ru.citeck.ecos.commands.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.json.Json

data class CommandResult(

    val id: String,

    val started: Long,
    val completed: Long,

    val command: Command,

    val appName: String,
    val appInstanceId: String,

    val result: JsonNode = NullNode.instance,
    val errors: List<CommandError> = emptyList(),

    @JsonIgnore
    val primaryError: Throwable? = null
) {

    fun <T : Any> getResultAs(type: Class<T>): T? {
        return Json.mapper.convert(result, type)
    }

    fun <T : Any> getCommandAs(type: Class<T>): T? {
        return Json.mapper.convert(command.body, type)
    }

    @JvmOverloads
    fun throwPrimaryErrorIfNotNull(actionIfErrorIsNotNull: Runnable? = null) {
        if (primaryError != null) {
            actionIfErrorIsNotNull?.run()
            CommandsService.log.error { "Throw error from command result: $this" }
            throw primaryError
        }
    }
}
