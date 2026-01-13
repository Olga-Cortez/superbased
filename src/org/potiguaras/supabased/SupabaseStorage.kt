package org.potiguaras.supabased

import com.google.appinventor.components.annotations.DesignerComponent
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.util.YailList
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Storage related stuff (implements storage-kt from supabase-kt kotlin library)",
    iconName = "icon.png"
)
@Suppress("FunctionName")
class SupabaseStorage(private val container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {

    private var CurrentBucket: String = ""

    init {
        form.registerForOnDestroy(this)
    }

    @SimpleProperty(description = "Set the current bucket name")
    fun CurrentBucket(bucketName: String) {
        CurrentBucket = bucketName
    }
    @SimpleProperty(description = "Get the current bucket name")
    fun CurrentBucket(): String = CurrentBucket

    @SimpleEvent(description = "Event triggered when a file upload is successful")
    fun UploadSuccess(publicUrl: String, bucketName: String, filePath: String) {
        EventDispatcher.dispatchEvent(this, "UploadSuccess", publicUrl, bucketName, filePath)
    }

    @SimpleEvent(description = "Event triggered when a file download is successful")
    fun DownloadSuccess(fileData: String, bucketName: String, filePath: String) {
        EventDispatcher.dispatchEvent(this, "DownloadSuccess", fileData, bucketName, filePath)
    }

    @SimpleEvent(description = "Event triggered when a storage operation error occurs")
    fun StorageError(error: String) {
        EventDispatcher.dispatchEvent(this, "StorageError", error)
    }

    @SimpleFunction(description = "Upload a file to the current bucket")
    fun UploadFile(filePath: String, fileData: String) {
        UploadToBucket(CurrentBucket, filePath, fileData)
    }

    @SimpleFunction(description = "Upload a file to a specific bucket")
    fun UploadToBucket(bucketName: String, filePath: String, fileData: String) {
        scope.launch {
            try {
                val bytes = Base64.decode(fileData, Base64.DEFAULT)
                SupabaseClient?.storage?.from(bucketName)?.upload(filePath, bytes) {
                    upsert = true
                }
                // Get Public URL
                val publicUrl = SupabaseClient?.storage?.from(bucketName)?.publicUrl(filePath) ?: ""
                mainHandler.launch { UploadSuccess(publicUrl, bucketName, filePath) }
            } catch (e: Exception) {
                mainHandler.launch { StorageError(e.message ?: e.toString()) }
            }
        }
    }

    @SimpleFunction(description = "Download a file from the current bucket")
    fun DownloadFile(filePath: String) {
        DownloadFromBucket(CurrentBucket, filePath)
    }

    @SimpleFunction(description = "Download a file from a specific bucket")
    fun DownloadFromBucket(bucketName: String, filePath: String) {
        scope.launch {
            try {
                val bytes = SupabaseClient?.storage?.from(bucketName)?.downloadAuthenticated(filePath)
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                mainHandler.launch { DownloadSuccess(base64, bucketName, filePath) }
            } catch (e: Exception) {
                mainHandler.launch { StorageError(e.message ?: e.toString()) }
            }
        }
    }

    @SimpleFunction(description = "List files in the current bucket")
    fun ListFiles() {
        ListFilesInBucket(CurrentBucket)
    }

    @SimpleFunction(description = "List files in a specific bucket")
    fun ListFilesInBucket(bucketName: String) {
        scope.launch {
            try {
                val files = SupabaseClient?.storage?.from(bucketName)?.list()
                val list = YailList.makeList(files?.map { it.name } as? List<Any> ?: emptyList<String>())
                mainHandler.launch { EventDispatcher.dispatchEvent(this@Superbased, "ListFilesSuccess", list, bucketName) }
            } catch (e: Exception) {
                mainHandler.launch { StorageError(e.message ?: e.toString()) }
            }
        }
    }


}