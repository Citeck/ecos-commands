package ru.citeck.ecos.commands.remote

import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.Future

interface RemoteCommandsService {

    fun init()

    fun execute(command: Command, config: CommandConfig) : Future<CommandResult>

    fun executeForGroup(command: Command, config: CommandConfig) : Future<List<CommandResult>>
}
