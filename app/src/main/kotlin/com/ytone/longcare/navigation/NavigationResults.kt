package com.ytone.longcare.navigation

import androidx.compose.runtime.saveable.Saver
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Only known, bounded metadata contracts are persisted. Never SDK objects or image bytes. */
internal class NavigationResults(
    private val values: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
) {
    private val flows = mutableMapOf<Pair<String, String>, MutableStateFlow<Any?>>()

    fun handle(id: String, canWrite: () -> Boolean = { true }) = EntryResults(this, id, canWrite)

    fun drop(id: String) {
        values.remove(id)
        flows.keys.filter { it.first == id }.forEach { flows.remove(it)?.value = null }
    }

    fun snapshot(): String = Json.encodeToString(values)

    internal fun flow(id: String, key: String): MutableStateFlow<Any?> =
        flows.getOrPut(id to key) { MutableStateFlow(values[id]?.get(key)?.let { decode(key, it) }) }

    internal fun set(id: String, key: String, value: Any?) {
        if (value == null) values[id]?.remove(key)
        else values.getOrPut(id) { mutableMapOf() }[key] = encode(key, value)
        flow(id, key).value = value
    }

    companion object {
        fun restore(value: String) = NavigationResults(Json.decodeFromString(value))
        val Saver = Saver<NavigationResults, String>(save = { it.snapshot() }, restore = ::restore)

        private fun encode(key: String, value: Any): String = when (key) {
            NavigationConstants.CAPTURED_IMAGE_URI_KEY, NavigationConstants.FACE_IMAGE_PATH_KEY ->
                Json.encodeToString(value as String)
            NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY -> Json.encodeToString(value as Boolean)
            NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, NavigationConstants.EXISTING_IMAGES_KEY -> {
                @Suppress("UNCHECKED_CAST")
                val images = value as Map<ImageTaskType, List<ImageTask>>
                Json.encodeToString(images.mapKeys { it.key.name }.mapValues { (_, list) -> list.map(ImageResult::from) })
            }
            else -> error("Unsupported navigation result key: $key")
        }

        private fun decode(key: String, value: String): Any = when (key) {
            NavigationConstants.CAPTURED_IMAGE_URI_KEY, NavigationConstants.FACE_IMAGE_PATH_KEY ->
                Json.decodeFromString<String>(value)
            NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY -> Json.decodeFromString<Boolean>(value)
            NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, NavigationConstants.EXISTING_IMAGES_KEY ->
                Json.decodeFromString<Map<String, List<ImageResult>>>(value)
                    .mapKeys { ImageTaskType.valueOf(it.key) }.mapValues { (_, list) -> list.map { it.toTask() } }
            else -> error("Unsupported navigation result key: $key")
        }
    }
}

internal class EntryResults(
    private val store: NavigationResults,
    private val id: String,
    private val canWrite: () -> Boolean,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> getStateFlow(key: String, initialValue: T?): StateFlow<T?> {
        require(initialValue == null) { "Navigation results start empty" }
        return store.flow(id, key) as StateFlow<T?>
    }

    fun <T> set(key: String, value: T) { if (canWrite()) store.set(id, key, value) }
    fun <T> remove(key: String) { if (canWrite()) store.set(id, key, null) }
}

@Serializable
private data class ImageResult(
    val id: String, val originalUri: String, val taskType: String,
    val resultUri: String?, val status: String, val errorMessage: String?,
    val isUploaded: Boolean, val key: String?, val cloudUrl: String?,
) {
    fun toTask() = ImageTask(id, originalUri, ImageTaskType.valueOf(taskType), resultUri,
        ImageTaskStatus.valueOf(status), errorMessage, isUploaded, key, cloudUrl)
    companion object {
        fun from(task: ImageTask) = with(task) {
            ImageResult(id, originalUri, taskType.name, resultUri, status.name, errorMessage, isUploaded, key, cloudUrl)
        }
    }
}
