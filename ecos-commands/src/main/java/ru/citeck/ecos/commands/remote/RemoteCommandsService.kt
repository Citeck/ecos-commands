package ru.citeck.ecos.commands.remote

import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.Future

interface RemoteCommandsService {

    fun execute(command: Command): Future<CommandResult>

    fun executeForGroup(command: Command): Future<List<CommandResult>>

    fun dispose()
}
