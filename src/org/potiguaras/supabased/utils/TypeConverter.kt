package org.potiguaras.supabased.utils

import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import kotlinx.serialization.json.*

object TypeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    fun yailDictionaryToJsonObject(dict: YailDictionary): JsonObject {
        val jsonMap = mutableMapOf<String, JsonElement>()
        for (key in dict.keys) {
            val value = dict[key]
            jsonMap[key.toString()] = valueToJsonElement(value)
        }
        return JsonObject(jsonMap)
    }

    fun yailDictToRpcParams(parameters: YailDictionary): JsonObject {
        return yailDictionaryToJsonObject(parameters)  // Reusa mesma lógica
    }

    // auxiliar para converter QUALQUER valor
    private fun valueToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is YailDictionary -> yailDictionaryToJsonObject(value)
            is YailList -> {
                val elements = mutableListOf<JsonElement>()
                for (i in 0 until value.size) {
                    elements.add(valueToJsonElement(value.getObject(i)))
                }
                JsonArray(elements)
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}