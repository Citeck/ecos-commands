package ru.citeck.ecos.commands.remote

import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import java.util.concurrent.Future

interface RemoteCommandsService {

    fun execute(command: CommandDto) : Future<CommandResultDto>
}
