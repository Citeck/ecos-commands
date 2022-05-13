package ru.citeck.ecos.commands.remote

import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.webapp.api.promise.Promise

interface RemoteCommandsService {

    fun execute(command: Command): Promise<CommandResult>

    fun executeForGroup(command: Command): Promise<List<CommandResult>>

    fun dispose()
}
