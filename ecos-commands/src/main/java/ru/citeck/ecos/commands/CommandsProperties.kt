package ru.citeck.ecos.commands

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import mu.KotlinLogging
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault

@IncludeNonDefault
@JsonDeserialize(builder = CommandsProperties.Builder::class)
data class CommandsProperties(
    val commandTimeoutMs: Long,
    val concurrentCommandConsumers: Int,
    val channelQos: Int,
    val listenBroadcast: Boolean
) {

    companion object {

        val DEFAULT = create {}
        val log = KotlinLogging.logger {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): CommandsProperties {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): CommandsProperties {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var commandTimeoutMs: Long = 60_000L
        var concurrentCommandConsumers: Int = 4
        var channelQos: Int = 1
        var listenBroadcast: Boolean = true

        constructor(base: CommandsProperties) : this() {
            commandTimeoutMs = base.commandTimeoutMs
            concurrentCommandConsumers = base.concurrentCommandConsumers
            channelQos = base.channelQos
            listenBroadcast = base.listenBroadcast
        }

        fun withCommandTimeoutMs(commandTimeoutMs: Long?): Builder {
            this.commandTimeoutMs = commandTimeoutMs ?: DEFAULT.commandTimeoutMs
            return this
        }

        fun withConcurrentCommandConsumers(concurrentCommandConsumers: Int?): Builder {
            this.concurrentCommandConsumers = concurrentCommandConsumers ?: DEFAULT.concurrentCommandConsumers
            return this
        }

        fun withChannelsQos(channelsQos: Int?): Builder {
            this.channelQos = channelsQos ?: DEFAULT.channelQos
            return this
        }

        fun withListenBroadcast(listenBroadcast: Boolean?): Builder {
            this.listenBroadcast = listenBroadcast ?: DEFAULT.listenBroadcast
            return this
        }

        fun build(): CommandsProperties {
            return CommandsProperties(
                commandTimeoutMs = commandTimeoutMs,
                concurrentCommandConsumers = concurrentCommandConsumers,
                channelQos = channelQos,
                listenBroadcast = listenBroadcast
            )
        }
    }
}
