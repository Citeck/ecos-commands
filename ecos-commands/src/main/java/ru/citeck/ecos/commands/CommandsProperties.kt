package ru.citeck.ecos.commands

class CommandsProperties(
    val commandTimeoutMs: Long = 60_000L,
    val concurrentCommandConsumers: Int = 4,
    val channelQos: Int = 1,
    val listenBroadcast: Boolean = true
)
