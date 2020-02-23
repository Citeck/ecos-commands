package ru.citeck.ecos.commands.remote

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.citeck.ecos.commands.dto.CommandConfig
import ru.citeck.ecos.commands.dto.CommandDto
import ru.citeck.ecos.commands.dto.CommandResultDto
import ru.citeck.ecos.commands.dto.ErrorDto
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class NoopRemoteCommandsService : RemoteCommandsService {

    companion object {
        val log: Logger = LoggerFactory.getLogger(NoopRemoteCommandsService::class.java)
    }

    override fun execute(command: CommandDto, config: CommandConfig) : Future<CommandResultDto> {
        val errorMsg = "Remote commands service is not defined. Command can't be executed"
        log.error("$errorMsg. Command: $command")
        return CompletableFuture.completedFuture(CommandResultDto(
            id = UUID.randomUUID().toString(),
            result = null,
            command = command,
            started = Instant.now().toEpochMilli(),
            completed = Instant.now().toEpochMilli(),
            errors = listOf(ErrorDto(
                type = "",
                message = errorMsg
            ))
        ))
    }
}
