package org.potiguaras.supabased
import com.google.appinventor.components.annotations.*
import com.google.appinventor.components.common.PropertyTypeConstants
import com.google.appinventor.components.runtime.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.functions.*
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.storage.*
import kotlinx.coroutines.*
@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Supabase core functionalities (required for all modules).",
    iconName = "icon.png"
)
@Suppress("FunctionName")
class SupabaseCore(private val container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {
    companion object {
        private var _client: SupabaseClient? = null
        private var _isInitialized = false
        val mainHandler = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun getClient(): SupabaseClient? = _client
        fun isInitialized(): Boolean = _isInitialized
        fun setClient(client: SupabaseClient) {
            _client = client
            _isInitialized = true
        }
        fun reset() {
            _client = null
            _isInitialized = false
        }
    }
    @Suppress("PrivatePropertyName")
    private var SupabaseUrl: String = ""
    @Suppress("PrivatePropertyName")
    private var SupabaseKey: String = ""
    init {form.registerForOnDestroy(this)
        reset()}
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
    @SimpleProperty(description = "Set the Supabase project URL")
    fun SupabaseUrl(url: String) {SupabaseUrl = url}
    @SimpleProperty(description = "Get the Supabase project URL")
    fun SupabaseUrl(): String = SupabaseUrl
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
    @SimpleProperty(description = "Set the Supabase API Key")
    fun SupabaseKey(key: String) {SupabaseKey = key}
    @SimpleProperty(description = "Get the Supabase API Key")
    fun SupabaseKey(): String = SupabaseKey
    @SimpleEvent(description = "Event triggered when the Supabase client is successfully initialized")
    fun ClientInitialized() {EventDispatcher.dispatchEvent(this, "ClientInitialized")}
    @SimpleEvent(description = "Event triggered when the Supabase client fails to initialize")
    fun ClientInitializationFailed(message: String) {EventDispatcher.dispatchEvent(this, "ClientInitializationFailed", message)}
    @SimpleFunction(description = "Initialize the Supabase client with URL and API Key")
    fun InitializeClient() {
        scope.launch {
            try {
                val client = createSupabaseClient(SupabaseUrl, SupabaseKey) {
                    install(Postgrest)
                    install(Auth)
                    install(Realtime)
                    install(Storage)
                    install(Functions)
                }
                setClient(client)
                mainHandler.launch { ClientInitialized() }
            } catch (t: Throwable) {
                mainHandler.launch { ClientInitializationFailed(t.message ?: t.toString()) }
            }
        }
    }
    override fun onDestroy() {
        reset()
    }
}