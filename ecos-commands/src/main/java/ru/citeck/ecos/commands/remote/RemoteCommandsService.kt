package ru.citeck.ecos.commands.remote

import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResult
import java.util.concurrent.Future

interface RemoteCommandsService {

    fun execute(command: CommandDto, config: CommandConfig) : Future<CommandResult>

    fun executeForGroup(command: CommandDto, config: CommandConfig) : Future<List<CommandResult>>
}
