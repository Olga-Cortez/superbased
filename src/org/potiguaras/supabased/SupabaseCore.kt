package org.potiguaras.supabased

import com.google.appinventor.components.annotations.*
import com.google.appinventor.components.common.PropertyTypeConstants
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.OnPauseListener
import com.google.appinventor.components.runtime.OnResumeListener
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
    iconName = "icon.png",
    nonVisible = true,
    category = com.google.appinventor.components.common.ComponentCategory.EXTENSION
)
@Suppress("FunctionName")
class SupabaseCore(
    private val container: ComponentContainer
) : AndroidNonvisibleComponent(container.`$form`()),
    OnDestroyListener,
    OnPauseListener,
    OnResumeListener {

    companion object {
        @Volatile
        private var _client: SupabaseClient? = null

        @Volatile
        private var _isInitialized = false

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

    private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableListOf<Job>()
    private var isPaused = false

    @Suppress("PrivatePropertyName")
    private var SupabaseUrl: String = ""

    @Suppress("PrivatePropertyName")
    private var SupabaseKey: String = ""

    init {
        form.registerForOnDestroy(this)
        form.registerForOnPause(this)
        form.registerForOnResume(this)
        reset()
    }

    @DesignerProperty(
        editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
        defaultValue = ""
    )
    @SimpleProperty(description = "Set the Supabase project URL")
    fun SupabaseUrl(url: String) {
        SupabaseUrl = url
    }

    @SimpleProperty(description = "Get the Supabase project URL")
    fun SupabaseUrl(): String = SupabaseUrl

    @DesignerProperty(
        editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
        defaultValue = ""
    )
    @SimpleProperty(description = "Set the Supabase API Key")
    fun SupabaseKey(key: String) {
        SupabaseKey = key
    }

    @SimpleProperty(description = "Get the Supabase API Key")
    fun SupabaseKey(): String = SupabaseKey

    @SimpleEvent(description = "Event triggered when the Supabase client is successfully initialized")
    fun ClientInitialized() {
        EventDispatcher.dispatchEvent(this, "ClientInitialized")
    }

    @SimpleEvent(description = "Event triggered when the Supabase client fails to initialize")
    fun ClientInitializationFailed(message: String) {
        EventDispatcher.dispatchEvent(this, "ClientInitializationFailed", message)
    }

    @SimpleFunction(description = "Initialize the Supabase client with URL and API Key")
    fun InitializeClient() {
        if (isPaused) {
            form.runOnUiThread {
                ClientInitializationFailed("Component is paused")
            }
            return
        }

        val job = componentScope.launch {
            try {
                if (SupabaseUrl.isEmpty() || SupabaseKey.isEmpty()) {
                    throw IllegalArgumentException("URL and API Key must be set")
                }

                val client = createSupabaseClient(SupabaseUrl, SupabaseKey) {
                    install(Postgrest)
                    install(Auth)
                    install(Realtime)
                    install(Storage)
                    install(Functions)
                }

                setClient(client)

                form.runOnUiThread {
                    ClientInitialized()
                }
            } catch (e: Exception) {
                form.runOnUiThread {
                    ClientInitializationFailed(e.message ?: e.toString())
                }
            }
        }

        trackJob(job)
    }

    @SimpleFunction(description = "Reset and clear the Supabase client")
    fun ResetClient() {
        cancelAllJobs()
        reset()
    }

    @SimpleFunction(description = "Check if client is ready for operations")
    fun IsClientReady(): Boolean {
        return isInitialized() && !isPaused
    }

    override fun onDestroy() {
        cancelAllJobs()
        componentScope.cancel()
        reset()
    }

    override fun onPause() {
        isPaused = true
        // Pause ongoing operations that can be paused
        activeJobs.forEach { job ->
            if (job.isActive) {
                // Mark for potential cancellation if needed
            }
        }
    }

    override fun onResume() {
        isPaused = false
    }

    private fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    private fun cancelAllJobs() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    // Helper method for other components to safely execute operations
    internal fun executeSafely(
        operationName: String,
        block: suspend (SupabaseClient) -> Unit
    ): Job? {
        if (!isInitialized() || isPaused) {
            return null
        }

        val job = componentScope.launch {
            try {
                val client = getClient() ?: throw IllegalStateException("Client not initialized")
                block(client)
            } catch (e: CancellationException) {
                // Operation was cancelled, do nothing
            } catch (e: Exception) {
                // Error handling should be done by the caller
                throw e
            }
        }

        trackJob(job)
        return job
    }
}