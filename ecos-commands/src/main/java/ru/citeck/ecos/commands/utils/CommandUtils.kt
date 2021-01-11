package ru.citeck.ecos.commands.utils

object CommandUtils {

    fun getTargetAppByAppInstanceId(instanceId: String): String {
        return "instance-$instanceId"
    }
}
