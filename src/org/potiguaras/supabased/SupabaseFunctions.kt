package org.potiguaras.supabased

import com.google.appinventor.components.annotations.DesignerComponent
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.util.JsonUtil
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.FunctionRegion
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.json.JSONException
import org.potiguaras.supabased.utils.TypeConverter

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Supabase Edge Functions",
    iconName = "icon.png",
    nonVisible = true,
    category = com.google.appinventor.components.common.ComponentCategory.EXTENSION
)
@Suppress("FunctionName", "unused")
class SupabaseFunctions(
    private val container: ComponentContainer
) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {

    private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableListOf<Job>()

    private var currentEdgeFunction: String = ""
    private var defaultRegion: String = "any"

    init {
        form.registerForOnDestroy(this)
    }

    @SimpleProperty(description = "Check if Supabase client is initialized")
    fun IsClientInitialized(): Boolean = SupabaseCore.isInitialized()

    @SimpleProperty(description = "Set the current Edge function name")
    fun CurrentEdgeFunction(edgeFunctionName: String) {
        currentEdgeFunction = edgeFunctionName
    }

    @SimpleProperty(description = "Get the current Edge function name")
    fun CurrentEdgeFunction(): String = currentEdgeFunction

    @SimpleProperty(description = "Set default region for function calls")
    fun DefaultRegion(region: String) {
        defaultRegion = region
    }

    @SimpleProperty(description = "Get default region for function calls")
    fun DefaultRegion(): String = defaultRegion

    // === EVENTS ===
    @SimpleEvent(description = "Triggered when Edge Function call completes successfully")
    fun FunctionSuccess(
        result: Any?,
        functionName: String,
        executionTime: Long,
        statusCode: Int,
        callInfo: YailDictionary
    ) {
        EventDispatcher.dispatchEvent(
            this, "FunctionSuccess",
            result, functionName, executionTime, statusCode, callInfo
        )
    }

    @SimpleEvent(description = "Triggered when Edge Function call fails")
    fun FunctionError(
        errorMessage: String,
        errorCode: String,
        functionName: String,
        statusCode: Int?,
        details: YailDictionary
    ) {
        EventDispatcher.dispatchEvent(
            this, "FunctionError",
            errorMessage, errorCode, functionName, statusCode, details
        )
    }

    // === MAIN FUNCTIONS ===
    @SimpleFunction(description = "Call the current Edge function with parameters")
    fun CallCurrentFunction(
        body: YailDictionary = YailDictionary(),
        region: String = defaultRegion,
        headers: YailDictionary = YailDictionary()
    ) {
        if (currentEdgeFunction.isEmpty()) {
            triggerError(
                "No function name set",
                "NO_FUNCTION_SET",
                "",
                null,
                YailDictionary().apply {
                    put("suggestion", "Set CurrentEdgeFunction property first")
                }
            )
            return
        }
        CallFunction(currentEdgeFunction, body, region, headers)
    }

    @SimpleFunction(description = "Call a specific Edge function with parameters")
    fun CallFunction(
        functionName: String,
        body: YailDictionary = YailDictionary(),
        region: String = defaultRegion,
        headers: YailDictionary = YailDictionary()
    ) {
        executeFunctionOperation(functionName) { client ->
            val startTime = System.currentTimeMillis()
            val jsonBody = if (body.isNotEmpty()) {
                TypeConverter.yailDictionaryToJsonObject(body)
            } else {
                null
            }
            val functionHeaders = convertHeaders(headers)
            val functionRegion = region.toFunctionRegion()

            // Check for auth token in headers
            val hasAuthHeader = functionHeaders.entries().any {
                it.key.equals("Authorization", ignoreCase = true)
            }

            // Pass the body and headers to the unified invoke method
            val response = invokeFunction(client, functionName, functionRegion, functionHeaders, jsonBody)
            val executionTime = System.currentTimeMillis() - startTime
            handleSuccess(response, functionName, executionTime, region, hasAuthHeader)
        }
    }

    @SimpleFunction(description = "Call Edge function with raw JSON string body")
    fun CallFunctionWithJson(
        functionName: String,
        jsonBody: String = "",
        region: String = defaultRegion,
        headers: YailDictionary = YailDictionary()
    ) {
        executeFunctionOperation(functionName) { client ->
            val startTime = System.currentTimeMillis()
            val functionHeaders = convertHeaders(headers)
            val functionRegion = region.toFunctionRegion()
            val body: String? = jsonBody.ifEmpty { null }

            // Check for auth token in headers
            val hasAuthHeader = functionHeaders.entries().any {
                it.key.equals("Authorization", ignoreCase = true)
            }

            // Pass the String body to the unified invoke method
            val response = invokeFunction(client, functionName, functionRegion, functionHeaders, body)
            val executionTime = System.currentTimeMillis() - startTime
            handleSuccess(response, functionName, executionTime, region, hasAuthHeader)
        }
    }

    @SimpleFunction(description = "Get available function regions")
    fun GetAvailableRegions(): YailList {
        val regions = listOf(
            "any", "ap-northeast-1", "ap-northeast-2", "ap-south-1",
            "ap-southeast-1", "ap-southeast-2", "ca-central-1", "eu-central-1",
            "eu-west-1", "eu-west-2", "eu-west-3", "sa-east-1",
            "us-east-1", "us-west-1", "us-west-2"
        )
        return YailList.makeList(regions)
    }

    // === CORE LOGIC ===
    /**
     * Unified function to invoke a Supabase Edge Function.
     * It properly handles the library's behavior of throwing RestException on non-2xx responses.
     */
    private suspend fun <T> invokeFunction(
        client: SupabaseClient,
        functionName: String,
        region: FunctionRegion,
        headers: Headers,
        body: T?
    ): HttpResponse {
        return try {
            // The supabase-kt library throws RestException for non-2xx status codes.
            // This is the correct usage pattern.
            if (body != null) {
                client.functions.invoke(
                    function = functionName,
                    body = body,
                    region = region,
                    headers = headers
                )
            } else {
                client.functions.invoke(
                    function = functionName,
                    region = region,
                    headers = headers
                )
            }
        } catch (e: RestException) {
            // This catch block is CRITICAL. It handles the 4xx/5xx errors thrown by the SDK.
            val errorDetails = YailDictionary().apply {
                put("function", functionName)
                put("exception", e::class.java.simpleName)
                put("message", e.message ?: "Unknown RestException")
                put("status_code", e.statusCode?.value)
                // The original error response body from your Edge Function is in `e.message`
                put("response_body", e.message)
            }
            // Trigger the error event. The status code is now correctly extracted.
            triggerError(
                e.message ?: "Edge Function failed with status ${e.statusCode?.value}",
                "FUNCTIONS_HTTP_ERROR",
                functionName,
                e.statusCode?.value,
                errorDetails
            )
            // Re-throw to be caught by the outer executeFunctionOperation handler
            throw e
        }
    }

    private suspend fun handleSuccess(
        response: HttpResponse,
        functionName: String,
        executionTime: Long,
        region: String,
        hasAuthHeader: Boolean
    ) {
        val responseText = response.bodyAsText()
        val parsedResult = parseFunctionResult(responseText, response.status.value)

        val callInfo = createCallInfo(
            functionName = functionName,
            executionTime = executionTime,
            statusCode = response.status.value,
            success = true,
            region = region,
            hasAuth = hasAuthHeader
        )

        form.runOnUiThread {
            FunctionSuccess(
                result = parsedResult,
                functionName = functionName,
                executionTime = executionTime,
                statusCode = response.status.value,
                callInfo = callInfo
            )
        }
    }

    // === PRIVATE UTILITIES ===
    private fun getClient(): SupabaseClient? = SupabaseCore.getClient()

    private fun executeFunctionOperation(
        functionName: String,
        operation: suspend (SupabaseClient) -> Unit
    ) {
        if (!SupabaseCore.isInitialized()) {
            triggerError(
                "Supabase client not initialized",
                "CLIENT_NOT_INIT",
                functionName,
                null,
                YailDictionary().apply {
                    put("suggestion", "Call SupabaseCore.InitializeClient first")
                }
            )
            return
        }

        val job = componentScope.launch {
            try {
                val client = getClient() ?: throw IllegalStateException("Client not initialized")
                operation(client)
            } catch (e: CancellationException) {
                // Operation was canceled, do nothing
            } catch (e: RestException) {
                // Already handled in invokeFunction, do not re-handle here.
            } catch (e: Exception) {
                handleError(e, functionName)
            }
        }
        trackJob(job)
    }

    private fun convertHeaders(headers: YailDictionary): Headers {
        return Headers.build {
            for (key in headers.keys) {
                val value = headers[key]
                if (key is String && value != null) {
                    append(key, value.toString())
                }
            }
        }
    }

    private fun String.toFunctionRegion(): FunctionRegion {
        return FunctionRegion.entries.find { it.value == this } ?: FunctionRegion.ANY
    }

    private fun parseFunctionResult(jsonText: String?, statusCode: Int): Any? {
        if (jsonText.isNullOrEmpty()) return null
        return try {
            JsonUtil.getObjectFromJson(jsonText, true)
        } catch (e: JSONException) {
            jsonText
        } catch (e: Exception) {
            jsonText
        }
    }

    private fun handleError(exception: Exception, functionName: String) {
        val errorDetails = YailDictionary().apply {
            put("function", functionName)
            put("exception", exception::class.java.simpleName)
            put("message", exception.message ?: "Unknown error")
            put("stacktrace", exception.stackTraceToString())
        }
        form.runOnUiThread {
            FunctionError(
                errorMessage = exception.message ?: "Unknown error",
                errorCode = exception::class.java.simpleName,
                functionName = functionName,
                statusCode = null,
                details = errorDetails
            )
        }
    }

    private fun triggerError(
        message: String,
        code: String,
        functionName: String,
        statusCode: Int?,
        details: YailDictionary
    ) {
        form.runOnUiThread {
            FunctionError(message, code, functionName, statusCode, details)
        }
    }

    private fun createCallInfo(
        functionName: String,
        executionTime: Long,
        statusCode: Int,
        success: Boolean,
        region: String,
        hasAuth: Boolean
    ): YailDictionary {
        return YailDictionary().apply {
            put("function", functionName)
            put("timestamp", System.currentTimeMillis())
            put("execution_time_ms", executionTime)
            put("status_code", statusCode)
            put("success", success)
            put("region", region)
            put("authenticated", hasAuth)
        }
    }

    private fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    override fun onDestroy() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        componentScope.cancel()
    }
}