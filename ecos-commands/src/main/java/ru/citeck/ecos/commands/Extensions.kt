package ru.citeck.ecos.commands

import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.utils.EcomObjUtils

fun <T> CommandResultDto.getResultData(type: Class<T>) : T? {

    if (this.result == null) {
        return null
    }
    return EcomObjUtils.mapper.convertValue(this.result, type)
}

fun <T> CommandResultDto.getCommandData(type: Class<T>) : T? {
    return EcomObjUtils.mapper.convertValue(this.command.body, type)
}
