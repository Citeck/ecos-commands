package ru.citeck.ecos.commands.spring

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = ["ru.citeck.ecos.commands.spring"])
open class CommandsAutoConfiguration
