package dev.plaing.runtime.state

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject

/**
 * Client-side state store for the plaing app.
 * Holds the current page, alert messages, stored entities, and entity collections.
 */
class StateStore {
    var currentPage by mutableStateOf("")
    var alertMessage by mutableStateOf<String?>(null)

    private val entities = mutableMapOf<String, JsonObject>()
    private val entityLists = mutableMapOf<String, MutableList<JsonObject>>()
    private val selectedEntities = mutableMapOf<String, JsonObject?>()

    fun storeEntity(name: String, data: JsonObject) {
        entities[name] = data
    }

    fun getEntity(name: String): JsonObject? = entities[name]

    fun storeEntityList(name: String, items: List<JsonObject>) {
        val list = entityLists.getOrPut(name) { mutableStateListOf() }
        list.clear()
        list.addAll(items)
    }

    fun addToEntityList(name: String, item: JsonObject) {
        val list = entityLists.getOrPut(name) { mutableStateListOf() }
        list.add(item)
    }

    fun getEntityList(name: String): List<JsonObject> = entityLists[name] ?: emptyList()

    fun selectEntity(name: String, data: JsonObject) {
        selectedEntities[name] = data
    }

    fun clearSelection(name: String) {
        selectedEntities[name] = null
    }

    fun getSelectedEntity(name: String): JsonObject? = selectedEntities[name]

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
