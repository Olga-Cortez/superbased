package org.potiguaras.supabased

import com.google.appinventor.components.annotations.DesignerComponent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.util.YailDictionary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.launch

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Storage related stuff (implements storage-kt from supabase-kt kotlin library)",
    iconName = "icon.png"
)
@Suppress("FunctionName")
class SupabaseFunctions(private val container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {
    init {
        form.registerForOnDestroy(this)
    }

    private var CurrentEdgeFunction: String = ""

    @SimpleProperty(description = "Set the current Edge function name")
    fun CurrentEdgeFunction(edgeFunctionName: String) {
        CurrentEdgeFunction = edgeFunctionName
    }
    @SimpleProperty(description = "Get the current Edge function name")
    fun CurrentEdgeFunction(): String = CurrentEdgeFunction

    @SimpleFunction(description = "Call a edge function with parameters")
    fun CallFunction(functionName: String, body: YailDictionary, region: String, headers: YailDictionary ) {
        scope.launch {
            try {
                val result = SupabaseClient?.functions?.invoke(
                    function = functionName,
                )
                val resultStr = result?.toString() ?: ""
                // Try to parse result as List or Dictionary, else String
                val yailResult = try {
                    jsonToYailList(resultStr)
                } catch (e: Exception) {
                    try {
                        jsonToYailDictionary(resultStr)
                    } catch (e2: Exception) {
                        //
                    }
                }
                mainHandler.launch { RPCSuccess(yailResult, functionName) }
            } catch (e: Exception) {
                mainHandler.launch { RPCError(e.message ?: e.toString()) }
            }
        }
    }
}