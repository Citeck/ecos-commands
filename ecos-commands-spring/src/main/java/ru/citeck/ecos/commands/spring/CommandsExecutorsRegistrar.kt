package ru.citeck.ecos.commands.spring

import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsService
import javax.annotation.PostConstruct

@Configuration
open class CommandsExecutorsRegistrar(
    val executors: List<CommandExecutor<*>>,
    val commandsService: CommandsService
) {

    private val log = KotlinLogging.logger {}

    @PostConstruct
    fun register() {
        executors.forEach {
            log.info { "Register command executor: ${it.javaClass}" }
            commandsService.addExecutor(it)
        }
    }
}

