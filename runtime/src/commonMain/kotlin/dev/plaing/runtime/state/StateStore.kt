package dev.plaing.runtime.state

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject

/**
 * Client-side state store for the plaing app.
 * Holds the current page, alert messages, and stored entities.
 */
class StateStore {
    var currentPage by mutableStateOf("")
    var alertMessage by mutableStateOf<String?>(null)

    private val entities = mutableMapOf<String, JsonObject>()

    fun storeEntity(name: String, data: JsonObject) {
        entities[name] = data
    }

    fun getEntity(name: String): JsonObject? = entities[name]

    fun navigateTo(page: String) {
        currentPage = page
        alertMessage = null
    }

    fun showAlert(message: String) {
        alertMessage = message
    }

    fun clearAlert() {
        alertMessage = null
    }
}
