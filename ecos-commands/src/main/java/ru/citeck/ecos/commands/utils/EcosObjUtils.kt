package ru.citeck.ecos.commands.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
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

    enum class DataType {
        JSON_TEXT
    }
}
