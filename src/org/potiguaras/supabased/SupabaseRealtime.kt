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
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Realtime subscriptions (implements realtime-kt from supabase-kt kotlin library)",
    iconName = "icon.png"
)
@Suppress("FunctionName")
class SupabaseRealtime(private val container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {

    // Coroutine scope for managing async operations
    private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableListOf<Job>()

    // Current channel and table names for realtime subscriptions
    private var currentChannelName: String = ""
    private var currentTableName: String = ""

    init {
        form.registerForOnDestroy(this)
    }

    /**
     * Set the current channel name for realtime subscriptions.
     * @param channelName The name of the channel to subscribe to
     */
    @SimpleProperty(description = "Set the current channel name")
    fun CurrentChannel(channelName: String) {
        currentChannelName = channelName
    }

    /**
     * Get the current channel name.
     * @return The current channel name
     */
    @SimpleProperty(description = "Get the current channel name")
    fun CurrentChannel(): String = currentChannelName

    /**
     * Set the current table name for database change subscriptions.
     * @param tableName The name of the database table to monitor
     */
    @SimpleProperty(description = "Set the current table name")
    fun CurrentTable(tableName: String) {
        currentTableName = tableName
    }

    /**
     * Get the current table name.
     * @return The current table name
     */
    @SimpleProperty(description = "Get the current table name")
    fun CurrentTable(): String = currentTableName

    /**
     * Event triggered when a database change is detected via realtime subscription.
     * @param channelName The name of the channel that received the change
     * @param tableName The name of the table that changed
     * @param eventType The type of database event (INSERT, UPDATE, DELETE)
     * @param payload Dictionary containing the changed data
     */
    @SimpleEvent(description = "Event triggered when a database change is detected via realtime subscription")
    fun DatabaseChange(channelName: String, tableName: String, eventType: String, payload: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "DatabaseChange", channelName, tableName, eventType, payload)
    }

    /**
     * Event triggered when an error occurs in realtime operations.
     * @param error The error message describing what went wrong
     * @param channelName The channel where the error occurred (if applicable)
     */
    @SimpleEvent(description = "Event triggered when a realtime operation error occurs")
    fun RealtimeError(error: String, channelName: String) {
        EventDispatcher.dispatchEvent(this, "RealtimeError", error, channelName)
    }

    /**
     * Subscribe to database changes on the current channel and table.
     * Uses the channel and table set via CurrentChannel and CurrentTable properties.
     * @param eventType The type of event to subscribe to (INSERT, UPDATE, DELETE, or * for all)
     */
    @SimpleFunction(description = "Subscribe to database changes on the current channel and table")
    fun SubscribeToChanges(eventType: String) {
        SubscribeToTableChanges(currentChannelName, currentTableName, eventType)
    }

    /**
     * Subscribe to database changes on a specific channel and table.
     * This is the core subscription method that handles the realtime connection.
     * @param channelName The name of the channel to subscribe to
     * @param tableName The name of the database table to monitor
     * @param eventType The type of event to subscribe to (INSERT, UPDATE, DELETE, or * for all)
     */
    @SimpleFunction(description = "Subscribe to database changes on a specific channel and table")
    fun SubscribeToTableChanges(channelName: String, tableName: String, eventType: String) {
        // Validate inputs
        if (!SupabaseCore.isInitialized()) {
            form.runOnUiThread {
                RealtimeError("Supabase client not initialized. Please call InitializeClient first.", channelName)
            }
            return
        }

        if (channelName.isEmpty()) {
            form.runOnUiThread {
                RealtimeError("Channel name cannot be empty", channelName)
            }
            return
        }

        if (tableName.isEmpty()) {
            form.runOnUiThread {
                RealtimeError("Table name cannot be empty", channelName)
            }
            return
        }

        // Launch subscription in a coroutine
        val job = componentScope.launch {
            try {
                val client = SupabaseCore.getClient()
                if (client == null) {
                    form.runOnUiThread {
                        RealtimeError("Failed to get Supabase client instance", channelName)
                    }
                    return@launch
                }

                // Create and configure channel
                val channel = client.realtime.channel(channelName)
                val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = tableName
                }

                // Subscribe to the channel
                channel.subscribe()

                // Collect and process changes
                flow.collect { action ->
                    val actionType = when (action) {
                        is PostgresAction.Insert -> "INSERT"
                        is PostgresAction.Update -> "UPDATE"
                        is PostgresAction.Delete -> "DELETE"
                        else -> "UNKNOWN"
                    }

                    val payloadStr = when (action) {
                        is PostgresAction.Insert -> action.record.toString()
                        is PostgresAction.Update -> action.record.toString()
                        is PostgresAction.Delete -> action.oldRecord.toString()
                        else -> "{}"
                    }

                    val payloadDict = jsonToYailDictionary(payloadStr)

                    // Dispatch event on UI thread
                    form.runOnUiThread {
                        DatabaseChange(channelName, tableName, actionType, payloadDict)
                    }
                }
            } catch (e: CancellationException) {
                // Job was cancelled, no error to report
            } catch (e: Exception) {
                // Report error with detailed context
                val errorMessage = "Error in realtime subscription: ${e.message ?: e.toString()}"
                form.runOnUiThread {
                    RealtimeError(errorMessage, channelName)
                }
            }
        }

        // Track the job for cleanup
        trackJob(job)
    }

    /**
     * Convert JSON string to YailDictionary.
     * Handles conversion errors gracefully.
     * @param jsonString The JSON string to convert
     * @return YailDictionary representation of the JSON
     */
    private fun jsonToYailDictionary(jsonString: String): YailDictionary {
        return try {
            JsonUtil.getObjectFromJson(jsonString, true) as? YailDictionary
                ?: YailDictionary()
        } catch (e: Exception) {
            // Return empty dictionary on parse error
            YailDictionary()
        }
    }

    /**
     * Track a coroutine job for lifecycle management.
     * @param job The job to track
     */
    private fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    /**
     * Cancel all active jobs.
     * Called during cleanup to prevent memory leaks.
     */
    private fun cancelAllJobs() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    /**
     * Cleanup method called when the component is destroyed.
     * Cancels all active subscriptions and cleans up resources.
     */
    override fun onDestroy() {
        cancelAllJobs()
        componentScope.cancel()
    }
}