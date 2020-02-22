package ru.citeck.ecos.commands.exceptions

import java.lang.RuntimeException

class ExecutorNotFound(val type: String) : RuntimeException("Executor is not found: '$type'")
