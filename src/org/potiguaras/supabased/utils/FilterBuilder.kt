package org.potiguaras.supabased.utils

import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import org.potiguaras.supabased.helpers.FilterOperator

object FilterBuilder {
    fun createFilter(
        column: String,
        operator: FilterOperator,
        value: Any?,
        negate: Boolean = false
    ): YailDictionary {
        val filter = YailDictionary()
        filter["column"] = column
        filter["operator"] = operator.toUnderlyingValue()
        filter["value"] = value
        filter["negate"] = negate
        return filter
    }

    fun and(vararg filters: YailDictionary): YailDictionary {
        val result = YailDictionary()
        result["logic"] = "and"
        result["filters"] = YailList.makeList(filters.toList())
        return result
    }

    fun or(vararg filters: YailDictionary): YailDictionary {
        val result = YailDictionary()
        result["logic"] = "or"
        result["filters"] = YailList.makeList(filters.toList())
        return result
    }

    fun not(filter: YailDictionary): YailDictionary {
        // Create a new dictionary and copy all properties
        val result = YailDictionary()
        for (key in filter.keys) {
            result[key] = filter[key]
        }
        result["negate"] = true
        return result
    }
}