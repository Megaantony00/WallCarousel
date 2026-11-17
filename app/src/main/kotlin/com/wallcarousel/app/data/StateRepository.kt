// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.data

import android.content.Context
import com.wallcarousel.app.data.model.AppState
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Single source of truth for the app state.
 *
 * Writes are serialized, go to a temporary file first and are then renamed so
 * that a crash cannot leave a truncated JSON document behind.
 */
class StateRepository(context: Context) {
  private val appContext = context.applicationContext
  private val stateFile = File(appContext.filesDir, "state.json")
  private val mutex = Mutex()
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val _state = MutableStateFlow<AppState?>(null)
  val state: StateFlow<AppState?> = _state.asStateFlow()

  suspend fun ensureLoaded(): AppState {
    _state.value?.let { return it }
    return mutex.withLock {
      _state.value ?: loadFromDisk().also { _state.value = it }
    }
  }

  /** Applies [transform] and persists the result. Returns the new state. */
  suspend fun update(transform: (AppState) -> AppState): AppState {
    ensureLoaded()
    return mutex.withLock {
      val current = _state.value ?: AppState()
      val next = transform(current)
      if (next != current) {
        withContext(Dispatchers.IO) { persist(next) }
        _state.value = next
      }
      next
    }
  }

  /** Removes every persisted setting. */
  suspend fun reset() {
    ensureLoaded()
    mutex.withLock {
      val empty = AppState()
      withContext(Dispatchers.IO) { persist(empty) }
      _state.value = empty
    }
  }

  private suspend fun loadFromDisk(): AppState = withContext(Dispatchers.IO) {
    if (!stateFile.exists()) return@withContext AppState()
    val text = stateFile.readText()
    runCatching {
      val root = json.parseToJsonElement(text).jsonObject
      val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
      val payload = if (version < SCHEMA_VERSION) migrateToCurrent(root) else root
      json.decodeFromJsonElement<AppState>(payload).also { state ->
        // Persist the migrated form once, so the next launch reads the new
        // schema directly.
        if (version < SCHEMA_VERSION) persist(state)
      }
    }.getOrElse { error ->
      if (error is SerializationException) {
        // Keep the unreadable file for manual recovery instead of deleting it.
        stateFile.renameTo(File(appContext.filesDir, "state.json.corrupt"))
      }
      AppState()
    }
  }

  /**
   * Schema 1 stored a single [com.wallcarousel.app.data.model.Schedule] kind per
   * target. Schema 2 stores independent triggers, so the old kind is translated
   * into the matching trigger and everything else stays off.
   */
  private fun migrateToCurrent(root: JsonObject): JsonObject = buildJsonObject {
    root.forEach { (key, value) ->
      when (key) {
        "schemaVersion" -> put(key, JsonPrimitive(SCHEMA_VERSION))
        "home", "lock" -> put(key, migrateTarget(value.jsonObject))
        else -> put(key, value)
      }
    }
    if ("schemaVersion" !in root) put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
  }

  private fun migrateTarget(target: JsonObject): JsonObject = buildJsonObject {
    target.forEach { (key, value) ->
      if (key != "schedule") put(key, value)
    }
    val legacy = target["schedule"]?.jsonObject
    val kind = legacy?.get("kind")?.jsonPrimitive?.contentOrNull
    val schedule = if (kind == null) {
      legacy ?: JsonObject(emptyMap())
    } else {
      buildJsonObject {
        when (kind) {
          "INTERVAL" -> put(
            "intervalMinutes",
            legacy["intervalMinutes"] ?: JsonPrimitive(120),
          )
          "DAILY" -> put("dailyMinutes", legacy["dailyMinutes"] ?: JsonArray(emptyList()))
          "WAKE" -> put("wake", JsonPrimitive(true))
        }
      }
    }
    put("schedule", schedule)
  }

  private fun persist(state: AppState) {
    val payload = json.encodeToString(state).toByteArray()
    val temp = File(appContext.filesDir, "state.json.tmp")
    FileOutputStream(temp).use { output ->
      output.write(payload)
      output.flush()
      output.fd.sync()
    }
    if (!temp.renameTo(stateFile)) {
      stateFile.delete()
      if (!temp.renameTo(stateFile)) {
        throw IllegalStateException("Unable to persist application state")
      }
    }
  }

  private companion object {
    const val SCHEMA_VERSION = 2
  }
}
