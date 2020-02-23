package ru.citeck.ecos.commands.utils

import ru.citeck.ecos.commons.json.Json
import kotlin.reflect.KClass

object EcomObjUtils {

    fun toBytes(obj: Any): Data {
        val bytes = Json.mapper.toBytes(obj)!!
        return Data(bytes, DataType.JSON_TEXT)
    }

    fun <T : Any> fromBytes(arr: ByteArray, dataType: DataType, type: KClass<T>) : T {
        return when (dataType) {
            DataType.JSON_TEXT -> Json.mapper.read(arr, type.java)!!
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
