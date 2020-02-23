package ru.citeck.ecos.commands

import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commons.json.Json

fun <T> CommandResultDto.getResultData(type: Class<T>) : T? {

    if (this.result == null) {
        return null
    }
    return Json.mapper.convert(this.result, type)
}

fun <T> CommandResultDto.getCommandData(type: Class<T>) : T? {
    return Json.mapper.convert(this.command.body, type)
}
