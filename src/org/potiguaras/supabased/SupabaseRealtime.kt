package org.potiguaras.supabased

import com.google.appinventor.components.annotations.DesignerComponent
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.util.YailDictionary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.launch

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Storage related stuff (implements storage-kt from supabase-kt kotlin library)",
    iconName = "icon.png"
)
@Suppress("FunctionName")
class SupabaseRealtime(private val container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {

    private var CurrentChannel: String = ""

    init {
        form.registerForOnDestroy(this)
    }

    @SimpleProperty(description = "Set the current channel name")
    fun CurrentChannel(channelName: String) {
        CurrentChannel = channelName
    }
    @SimpleProperty(description = "Get the current channel name")
    fun CurrentChannel(): String = CurrentChannel

    @SimpleEvent(description = "Event triggered when a database change is detected via realtime subscription")
    fun DatabaseChange(channelName: String, tableName: String, eventType: String, payload: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "DatabaseChange", channelName, tableName, eventType, payload)
    }

    @SimpleFunction(description = "Subscribe to database changes on the current channel and table")
    fun SubscribeToChanges(eventType: String) {
        SubscribeToTableChanges(CurrentChannel, CurrentTable, eventType)
    }

    @SimpleFunction(description = "Subscribe to database changes on a specific channel and table")
    fun SubscribeToTableChanges(channelName: String, tableName: String, eventType: String) {
        scope.launch {
            try {
                val channel = SupabaseClient?.realtime?.channel(channelName)
                val flow = channel?.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = tableName
                }

                channel?.subscribe()

                flow?.collect { action ->
                    val payloadStr = when(action) {
                        is PostgresAction.Insert -> action.record.toString()
                        is PostgresAction.Update -> action.record.toString()
                        is PostgresAction.Delete -> action.oldRecord.toString()
                        else -> "{}"
                    }
                    val payloadDict = jsonToYailDictionary(payloadStr)
                    mainHandler.launch {
                        DatabaseChange(channelName, tableName, eventType, payloadDict)
                    }
                }
            } catch (e: Exception) {
                mainHandler.launch { EventDispatcher.dispatchEvent(this@Superbased, "RealtimeError", e.message ?: e.toString()) }
            }
        }
    }

}