package ru.citeck.ecos.commands.utils

import ecos.com.fasterxml.jackson210.databind.ObjectMapper
import ecos.com.fasterxml.jackson210.datatype.jsr310.JavaTimeModule
import ecos.com.fasterxml.jackson210.module.kotlin.KotlinModule
import kotlin.reflect.KClass


object EcomObjUtils {

    val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    init {
        mapper.registerModule(JavaTimeModule())
    }

    fun toBytes(obj: Any): Data {
        val bytes = mapper.writeValueAsBytes(obj)
        return Data(bytes, DataType.JSON_TEXT)
    }

    fun <T : Any> fromBytes(arr: ByteArray, dataType: DataType, type: KClass<T>) : T {
        return when (dataType) {
            DataType.JSON_TEXT -> mapper.readValue(arr, type.java)
        }
    }

    class Data(
        val bytes: ByteArray,
        val dataType: DataType
    )

    // Do not change an order
    enum class DataType {
        JSON_TEXT
    }
}
