package ru.citeck.ecos.commands

import java.time.Duration

data class CommandOptions(val ttl: Duration = Duration.ZERO)
