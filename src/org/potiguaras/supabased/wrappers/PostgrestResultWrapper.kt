package org.potiguaras.supabased.wrappers

import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.ktor.http.Headers
import kotlinx.serialization.json.*
import java.util.logging.Logger

class PostgrestResultWrapper(private val original: PostgrestResult) {

    companion object {
        private val LOGGER = Logger.getLogger(PostgrestResultWrapper::class.java.name)
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }

    private val parsedJson: JsonElement? = try {
        json.parseToJsonElement(original.data)
    } catch (e: Exception) {
        LOGGER.warning("Failed to parse JSON: ${e.message}")
        null
    }

    val headers: Headers = original.headers
    val count: Long? = original.countOrNull()
    val range: LongRange? = original.rangeOrNull()
    val rawData: String = original.data
    val success: Boolean = parsedJson != null

    fun toYailList(): YailList {
        return when (parsedJson) {
            is JsonArray -> convertJsonArray(parsedJson)
            is JsonElement -> YailList.makeList(listOf(convertJsonElement(parsedJson)))
            else -> YailList.makeEmptyList()
        }
    }

    fun toYailDictionary(): YailDictionary {
        val dict = YailDictionary()

        // Add metadata
        dict["_metadata"] = YailDictionary().apply {
            this["success"] = success
            this["count"] = count
            this["range_start"] = range?.first
            this["range_end"] = range?.last
            this["has_data"] = rawData.isNotEmpty() && rawData != "[]" && rawData != "{}"
            this["raw_data"] = rawData
        }

        // Add actual data
        if (parsedJson != null) {
            dict["data"] = convertJsonElement(parsedJson)
            dict["type"] = when (parsedJson) {
                is JsonObject -> "object"
                is JsonArray -> "array"
                is JsonPrimitive -> "primitive"
            }
        } else {
            dict["data"] = rawData
            dict["type"] = "raw"
            dict["error"] = "Failed to parse JSON"
        }

        return dict
    }

    private fun convertJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonObject -> convertJsonObject(element)
            is JsonArray -> convertJsonArray(element)
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.doubleOrNull != null -> element.double
                element.longOrNull != null -> element.long
                // Removido element.isNull - JsonPrimitive não tem essa propriedade
                // Em vez disso, verificamos se é JsonNull antes
                else -> element.content
            }
            // Adicionado tratamento para JsonNull explicitamente
            is JsonNull -> null
            else -> null
        }
    }

    private fun convertJsonObject(jsonObj: JsonObject): YailDictionary {
        val dict = YailDictionary()
        for ((key, value) in jsonObj) {
            dict[key] = convertJsonElement(value)
        }
        return dict
    }

    private fun convertJsonArray(jsonArray: JsonArray): YailList {
        val list = mutableListOf<Any?>()
        for (element in jsonArray) {
            list.add(convertJsonElement(element))
        }
        // Filtra nulos antes de criar a lista
        return YailList.makeList(list.filterNotNull())
    }
}