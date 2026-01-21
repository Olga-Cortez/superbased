package org.potiguaras.supabased

import com.google.appinventor.components.annotations.*
import com.google.appinventor.components.common.PropertyTypeConstants
import com.google.appinventor.components.runtime.*
import com.google.appinventor.components.runtime.util.JsonUtil
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.*
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.potiguaras.supabased.helpers.*
import org.potiguaras.supabased.utils.TypeConverter
import org.potiguaras.supabased.wrappers.PostgrestResultWrapper

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using PostgREST (database operations)",
    iconName = "icon.png",
    nonVisible = true,
    category = com.google.appinventor.components.common.ComponentCategory.EXTENSION
)
@Suppress("FunctionName", "unused")
class SupabasePostgrest(container: ComponentContainer) :
    AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {

    private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableListOf<Job>()

    private var currentTable: String = ""
    private var currentColumns: YailList = YailList.makeEmptyList()
    private var defaultSchema: String = "public"
    private var defaultCount: Int = CountOptions.NONE.toUnderlyingValue()
    private var defaultReturnData: Boolean = false
    private var defaultUpsert: Boolean = false
    private var defaultIgnoreDuplicates: Boolean = false
    private var defaultOnConflict: String = ""

    init {
        form.registerForOnDestroy(this)
    }

    @SimpleProperty(description = "Check if Supabase client is initialized")
    fun IsClientInitialized(): Boolean = SupabaseCore.isInitialized()

    @SimpleProperty(description = "Current table name for database operations")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING)
    fun CurrentTable(tableName: String) {
        currentTable = tableName
    }

    @SimpleProperty
    fun CurrentTable(): String = currentTable

    @SimpleProperty(description = "Current columns list for database operations")
    fun CurrentColumns(columns: YailList) {
        currentColumns = columns
    }

    @SimpleProperty
    fun CurrentColumns(): YailList = currentColumns

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "public")
    @SimpleProperty(description = "Default schema for operations")
    fun DefaultSchema(schema: String) {
        defaultSchema = schema
    }

    @SimpleProperty
    fun DefaultSchema(): String = defaultSchema

    @DesignerProperty(
        editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER,
        defaultValue = "0"
    )
    @SimpleProperty(description = "Default count option for queries (0=None, 1=Exact, 2=Planned, 3=Estimated)")
    fun DefaultCount(countOption: Int) {
        defaultCount = countOption
    }

    @SimpleProperty
    fun DefaultCount(): Int = defaultCount

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
    @SimpleProperty(description = "Default setting for returning data after insert/update/delete")
    fun DefaultReturnData(returnData: Boolean) {
        defaultReturnData = returnData
    }

    @SimpleProperty
    fun DefaultReturnData(): Boolean = defaultReturnData

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
    @SimpleProperty(description = "Default upsert behavior for insert operations")
    fun DefaultUpsert(upsert: Boolean) {
        defaultUpsert = upsert
    }

    @SimpleProperty
    fun DefaultUpsert(): Boolean = defaultUpsert

    // === EVENTS ===

    @SimpleEvent(description = "Triggered when SELECT query completes successfully")
    fun SelectSuccess(data: YailList, tableName: String, count: Long, totalCount: Long?, queryInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "SelectSuccess", data, tableName, count, totalCount, queryInfo)
    }

    @SimpleEvent(description = "Triggered when INSERT operation completes successfully")
    fun InsertSuccess(insertedData: YailList, tableName: String, count: Int, queryInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "InsertSuccess", insertedData, tableName, count, queryInfo)
    }

    @SimpleEvent(description = "Triggered when UPDATE operation completes successfully")
    fun UpdateSuccess(updatedData: YailList, tableName: String, count: Int, queryInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "UpdateSuccess", updatedData, tableName, count, queryInfo)
    }

    @SimpleEvent(description = "Triggered when DELETE operation completes successfully")
    fun DeleteSuccess(deletedData: YailList, tableName: String, count: Int, queryInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "DeleteSuccess", deletedData, tableName, count, queryInfo)
    }

    @SimpleEvent(description = "Triggered when RPC function call completes successfully")
    fun RPCSuccess(result: YailDictionary, functionName: String, executionTime: Long, queryInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "RPCSuccess", result, functionName, executionTime, queryInfo)
    }

    @SimpleEvent(description = "Triggered when any database operation fails")
    fun DatabaseError(errorMessage: String, errorCode: String, operation: String, details: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "DatabaseError", errorMessage, errorCode, operation, details)
    }

    // === MAIN OPERATIONS ===

    @SimpleFunction(description = "SELECT from current table with optional filters")
    fun Select(
        columns: YailList = YailList.makeEmptyList(),
        filters: YailDictionary? = null,
        orderBy: String = "",
        orderDirection: Int = OrderOptions.ASC.toUnderlyingValue(),
        limit: Int = 0,
        offset: Int = 0,
        countOption: Int = -1
    ) {
        SelectFrom(currentTable, columns, filters, orderBy, orderDirection, limit, offset, countOption)
    }

    @SimpleFunction(description = "SELECT from specific table")
    fun SelectFrom(
        tableName: String,
        columns: YailList = YailList.makeEmptyList(),
        filters: YailDictionary? = null,
        orderBy: String = "",
        orderDirection: Int = OrderOptions.ASC.toUnderlyingValue(),
        limit: Int = 0,
        offset: Int = 0,
        countOption: Int = -1
    ) {
        executeDbOperation("SELECT", tableName) {
            val client = getClient() ?: throw Exception("Supabase client not initialized")
            val startTime = System.currentTimeMillis()

            val columnsStr = if (columns.isNotEmpty()) listToColumns(columns) else "*"
            val countOpt = if (countOption == -1) defaultCount else countOption

            val result = client.from(tableName, defaultSchema).select(
                columns = Columns.raw(columnsStr)
            ) {
                // Apply filters if provided
                if (filters != null) {
                    applyFilters(this, filters)
                }

                // Apply ordering
                if (orderBy.isNotEmpty()) {
                    val order = OrderOptions.fromUnderlyingValue(orderDirection) ?: OrderOptions.ASC
                    this.order(orderBy, when (order) {
                        OrderOptions.ASC -> Order.ASCENDING
                        OrderOptions.DESC -> Order.DESCENDING
                    })
                }

                // Apply limit and offset
                if (limit > 0) {
                    this.limit(limit.toLong())
                }
                if (offset > 0) {
                    this.range(offset.toLong(), (offset + if (limit > 0) limit else 1000).toLong())
                }

                // Apply count option
                if (countOpt != CountOptions.NONE.toUnderlyingValue()) {
                    val countType = when (CountOptions.fromUnderlyingValue(countOpt)) {
                        CountOptions.EXACT -> Count.EXACT
                        CountOptions.PLANNED -> Count.PLANNED
                        CountOptions.ESTIMATED -> Count.ESTIMATED
                        else -> Count.EXACT
                    }
                    this.count(countType)
                }
            }

            val wrapper = PostgrestResultWrapper(result)
            val data = wrapper.toYailList()
            val totalCount = wrapper.count
            val executionTime = System.currentTimeMillis() - startTime

            val queryInfo = createQueryInfo(
                operation = "SELECT",
                table = tableName,
                executionTime = executionTime,
                count = data.size.toLong(),
                totalCount = totalCount,
                success = true
            )

            form.runOnUiThread {
                SelectSuccess(data, tableName, data.size.toLong(), totalCount, queryInfo)
            }
        }
    }

    @SimpleFunction(description = "INSERT into current table")
    fun Insert(
        data: YailDictionary,
        returnData: Boolean = defaultReturnData,
        upsert: Boolean = defaultUpsert,
        onConflict: String = defaultOnConflict,
        ignoreDuplicates: Boolean = defaultIgnoreDuplicates
    ) {
        InsertInto(currentTable, data, returnData, upsert, onConflict, ignoreDuplicates)
    }

    @SimpleFunction(description = "INSERT into specific table")
    fun InsertInto(
        tableName: String,
        data: YailDictionary,
        returnData: Boolean = defaultReturnData,
        upsert: Boolean = defaultUpsert,
        onConflict: String = defaultOnConflict,
        ignoreDuplicates: Boolean = defaultIgnoreDuplicates
    ) {
        executeDbOperation("INSERT", tableName) {
            val client = getClient() ?: throw Exception("Supabase client not initialized")
            val startTime = System.currentTimeMillis()

            val jsonData = TypeConverter.yailDictionaryToJsonObject(data)
            val jsonArray = JsonArray(listOf(jsonData))

            val result = if (upsert) {
                client.from(tableName, defaultSchema).upsert(jsonArray) {
                    if (onConflict.isNotEmpty()) {
                        this.onConflict = onConflict
                    }
                    this.ignoreDuplicates = ignoreDuplicates
                    if (returnData) {
                        select()
                    }
                    count(Count.EXACT)
                }
            } else {
                client.from(tableName, defaultSchema).insert(jsonArray) {
                    if (returnData) {
                        select()
                    }
                    count(Count.EXACT)
                }
            }

            val wrapper = PostgrestResultWrapper(result)
            val insertedData = wrapper.toYailList()
            val count = insertedData.size
            val executionTime = System.currentTimeMillis() - startTime

            val queryInfo = createQueryInfo(
                operation = if (upsert) "UPSERT" else "INSERT",
                table = tableName,
                executionTime = executionTime,
                count = count.toLong(),
                success = true,
                upsert = upsert,
                onConflict = onConflict.takeIf { it.isNotEmpty() }
            )

            form.runOnUiThread {
                InsertSuccess(insertedData, tableName, count, queryInfo)
            }
        }
    }

    @SimpleFunction(description = "UPDATE current table with filters")
    fun Update(
        data: YailDictionary,
        filters: YailDictionary? = null,
        returnData: Boolean = defaultReturnData
    ) {
        UpdateTable(currentTable, data, filters, returnData)
    }

    @SimpleFunction(description = "UPDATE specific table")
    fun UpdateTable(
        tableName: String,
        data: YailDictionary,
        filters: YailDictionary? = null,
        returnData: Boolean = defaultReturnData
    ) {
        executeDbOperation("UPDATE", tableName) {
            val client = getClient() ?: throw Exception("Supabase client not initialized")
            val startTime = System.currentTimeMillis()

            val jsonData = TypeConverter.yailDictionaryToJsonObject(data)

            val result = client.from(tableName, defaultSchema).update(jsonData) {
                // Apply filters
                if (filters != null) {
                    applyFilters(this, filters)
                }

                if (returnData) {
                    select()
                }
                count(Count.EXACT)
            }

            val wrapper = PostgrestResultWrapper(result)
            val updatedData = wrapper.toYailList()
            val count = updatedData.size
            val executionTime = System.currentTimeMillis() - startTime

            val queryInfo = createQueryInfo(
                operation = "UPDATE",
                table = tableName,
                executionTime = executionTime,
                count = count.toLong(),
                success = true
            )

            form.runOnUiThread {
                UpdateSuccess(updatedData, tableName, count, queryInfo)
            }
        }
    }

    @SimpleFunction(description = "DELETE from current table with filters")
    fun Delete(filters: YailDictionary? = null, returnData: Boolean = defaultReturnData) {
        DeleteFrom(currentTable, filters, returnData)
    }

    @SimpleFunction(description = "DELETE from specific table")
    fun DeleteFrom(
        tableName: String,
        filters: YailDictionary? = null,
        returnData: Boolean = defaultReturnData
    ) {
        executeDbOperation("DELETE", tableName) {
            val client = getClient() ?: throw Exception("Supabase client not initialized")
            val startTime = System.currentTimeMillis()

            val result = client.from(tableName, defaultSchema).delete {
                // Apply filters
                if (filters != null) {
                    applyFilters(this, filters)
                }

                if (returnData) {
                    select()
                }
                count(Count.EXACT)
            }

            val wrapper = PostgrestResultWrapper(result)
            val deletedData = wrapper.toYailList()
            val count = deletedData.size
            val executionTime = System.currentTimeMillis() - startTime

            val queryInfo = createQueryInfo(
                operation = "DELETE",
                table = tableName,
                executionTime = executionTime,
                count = count.toLong(),
                success = true
            )

            form.runOnUiThread {
                DeleteSuccess(deletedData, tableName, count, queryInfo)
            }
        }
    }

    @SimpleFunction(description = "Call RPC function")
    fun CallRPC(
        functionName: String,
        parameters: YailDictionary = YailDictionary()
    ) {
        executeDbOperation("RPC", functionName) {
            val client = getClient() ?: throw Exception("Supabase client not initialized")
            val startTime = System.currentTimeMillis()

            val jsonParams = TypeConverter.yailDictionaryToJsonObject(parameters)

            val result = client.postgrest.rpc(
                function = functionName,
                parameters = jsonParams
            )

            val wrapper = PostgrestResultWrapper(result)
            val resultData = wrapper.toYailDictionary()
            val executionTime = System.currentTimeMillis() - startTime

            val queryInfo = createQueryInfo(
                operation = "RPC",
                function = functionName,
                executionTime = executionTime,
                success = true
            )

            form.runOnUiThread {
                RPCSuccess(resultData, functionName, executionTime, queryInfo)
            }
        }
    }

    // === HELPER FUNCTIONS ===

    @SimpleFunction(description = "Create a typed parameter for RPC calls")
    fun CreateTypedParam(
        name: String,
        value: Any?,
        type: Int,
        length: Int? = null,
        precision: Int? = null,
        scale: Int? = null
    ): YailDictionary {
        val param = YailDictionary()
        param["name"] = name
        param["value"] = value
        param["type"] = type

        length?.let { param["length"] = it }
        precision?.let { param["precision"] = it }
        scale?.let { param["scale"] = it }

        return param
    }

    @SimpleFunction(description = "Build parameters dictionary from typed parameters list")
    fun BuildParams(vararg typedParams: YailDictionary): YailDictionary {
        val params = YailDictionary()

        for (param in typedParams) {
            val name = param["name"]?.toString() ?: continue
            val type = param["type"] as? Int ?: RPCFunctionReturnType.Text.toUnderlyingValue()
            val value = param["value"]

            // Convert value based on type
            val convertedValue = when (RPCFunctionReturnType.fromUnderlyingValue(type)) {
                RPCFunctionReturnType.Json, RPCFunctionReturnType.Jsonb -> {
                    value as? String ?: JsonUtil.encodeJsonObject(value)
                }
                RPCFunctionReturnType.Bytea -> {
                    if (value is ByteArray) {
                        "\\x${value.joinToString("") { "%02x".format(it) }}"
                    } else {
                        value?.toString()
                    }
                }
                else -> value
            }

            params[name] = convertedValue
        }

        return params
    }

    @SimpleFunction(description = "Get all available RPC return types")
    fun GetReturnTypes(): YailList {
        val types = mutableListOf<String>()
        RPCFunctionReturnType.entries.forEach { type ->
            types.add(type.name)
        }
        return YailList.makeList(types)
    }

    // === PRIVATE METHODS ===

    private fun getClient(): SupabaseClient? {
        return SupabaseCore.getClient()
    }

    private fun executeDbOperation(operation: String, context: String, block: suspend () -> Unit) {
        if (!SupabaseCore.isInitialized()) {
            form.runOnUiThread {
                DatabaseError(
                    errorMessage = "Supabase client not initialized",
                    errorCode = "CLIENT_NOT_INIT",
                    operation = operation,
                    details = YailDictionary().apply {
                        this["context"] = context
                        this["suggestion"] = "Call SupabaseCore.InitializeClient first"
                    }
                )
            }
            return
        }

        val job = componentScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                // Operation was cancelled, do nothing
            } catch (e: Exception) {
                handleError(e, operation, context)
            }
        }

        trackJob(job)
    }

    private fun listToColumns(columnsList: YailList): String {
        val columns = mutableListOf<String>()
        for (i in 0 until columnsList.size) {
            columns.add(columnsList.getString(i))
        }
        return columns.joinToString(",")
    }

    private fun applyFilters(builder: PostgrestRequestBuilder, filter: YailDictionary) {
        builder.filter {
            applyFilterToBuilder(this, filter)
        }
    }

    private fun applyFilterToBuilder(filterBuilder: PostgrestFilterBuilder, filter: YailDictionary) {
        val logic = filter["logic"] as? String

        when (logic) {
            "and" -> {
                // Filters are automatically ANDed when chained
                val filters = filter["filters"] as? YailList ?: return
                for (i in 0 until filters.size) {
                    val f = filters.getObject(i) as? YailDictionary ?: continue
                    applySingleFilter(filterBuilder, f)
                }
            }
            "or" -> {
                // Use OR with lambda block
                val filters = filter["filters"] as? YailList ?: return
                filterBuilder.or {
                    for (i in 0 until filters.size) {
                        val f = filters.getObject(i) as? YailDictionary ?: continue
                        applySingleFilter(this, f)
                    }
                }
            }
            else -> {
                // Single filter
                applySingleFilter(filterBuilder, filter)
            }
        }
    }

    private fun applySingleFilter(filterBuilder: PostgrestFilterBuilder, filter: YailDictionary) {
        val column = filter["column"]?.toString() ?: return
        val operator = FilterOperator.fromUnderlyingValue(filter["operator"] as? Int ?: 0) ?: FilterOperator.EQ
        val value = filter["value"]
        val negate = filter["negate"] as? Boolean ?: false

        // Apply filter based on operator
        when (operator) {
            FilterOperator.EQ -> {
                if (value != null) {
                    if (negate) filterBuilder.neq(column, value) else filterBuilder.eq(column, value)
                }
            }
            FilterOperator.NEQ -> {
                if (value != null) {
                    if (negate) filterBuilder.eq(column, value) else filterBuilder.neq(column, value)
                }
            }
            FilterOperator.GT -> value?.let { if (negate) filterBuilder.lte(column, it) else filterBuilder.gt(column, it) }
            FilterOperator.GTE -> value?.let { if (negate) filterBuilder.lt(column, it) else filterBuilder.gte(column, it) }
            FilterOperator.LT -> value?.let { if (negate) filterBuilder.gte(column, it) else filterBuilder.lt(column, it) }
            FilterOperator.LTE -> value?.let { if (negate) filterBuilder.gt(column, it) else filterBuilder.lte(column, it) }
            FilterOperator.LIKE -> value?.toString()?.let { filterBuilder.like(column, it) }
            FilterOperator.ILIKE -> value?.toString()?.let { filterBuilder.ilike(column, it) }
            FilterOperator.IS -> value?.let { if (negate) filterBuilder.neq(column, it) else filterBuilder.eq(column, it) }
            FilterOperator.IN -> {
                // FIXED: Use proper IN operator if available in supabase-kt
                if (value is YailList && !negate) {
                    val list = mutableListOf<Any>()
                    for (i in 0 until value.size) {
                        value.getObject(i)?.let { list.add(it) }
                    }
                    if (list.isNotEmpty()) {
                        // Check if supabase-kt has in_ method
                        try {
                            // Try to use the proper in_ method if available
                            filterBuilder::class.java.getMethod("in_", String::class.java, List::class.java)
                                .invoke(filterBuilder, column, list)
                        } catch (e: NoSuchMethodException) {
                            // Fallback to multiple eq conditions in OR block
                            filterBuilder.or {
                                for (item in list) {
                                    eq(column, item)
                                }
                            }
                        }
                    }
                }
            }
            FilterOperator.CS -> {
                if (value is YailList) {
                    val list = mutableListOf<Any>()
                    for (i in 0 until value.size) {
                        value.getObject(i)?.let { list.add(it) }
                    }
                    if (list.isNotEmpty()) {
                        filterBuilder.cs(column, list)
                    }
                }
            }
            FilterOperator.CD -> {
                if (value is YailList) {
                    val list = mutableListOf<Any>()
                    for (i in 0 until value.size) {
                        value.getObject(i)?.let { list.add(it) }
                    }
                    if (list.isNotEmpty()) {
                        filterBuilder.cd(column, list)
                    }
                }
            }
        }
    }

    private fun handleError(exception: Exception, operation: String, context: String) {
        val errorDetails = YailDictionary().apply {
            this["operation"] = operation
            this["context"] = context
            this["exception"] = exception::class.java.simpleName
            this["message"] = exception.message ?: "Unknown error"
            this["stacktrace"] = exception.stackTraceToString()
        }

        form.runOnUiThread {
            DatabaseError(
                errorMessage = exception.message ?: "Unknown error",
                errorCode = exception::class.java.simpleName,
                operation = operation,
                details = errorDetails
            )
        }
    }

    private fun createQueryInfo(
        operation: String,
        table: String? = null,
        function: String? = null,
        executionTime: Long,
        count: Long = 0,
        totalCount: Long? = null,
        success: Boolean = true,
        upsert: Boolean? = null,
        onConflict: String? = null
    ): YailDictionary {
        return YailDictionary().apply {
            this["operation"] = operation
            this["timestamp"] = System.currentTimeMillis()
            this["execution_time_ms"] = executionTime
            this["success"] = success

            table?.let { this["table"] = it }
            function?.let { this["function"] = it }

            if (count > 0) {
                this["affected_count"] = count
            }

            totalCount?.let {
                this["total_count"] = it
                this["has_more"] = totalCount > count
            }

            upsert?.let { this["upsert"] = it }
            onConflict?.let { this["on_conflict"] = it }
        }
    }

    private fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    override fun onDestroy() {
        // Cancel all ongoing database operations
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        componentScope.cancel()
    }
}