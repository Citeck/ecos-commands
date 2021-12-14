package ru.citeck.ecos.commands

class CommandsProperties {

    var appName = ""
    var appInstanceId = ""

    var commandTimeoutMs = 60_000L

    var concurrentCommandConsumers = 4

    var channelQos = 1

    var listenBroadcast = true
}
