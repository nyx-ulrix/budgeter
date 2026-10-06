package com.nyxulrix.budgeter.data

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/**
 * The whole app state in memory, written to one JSON file after every change.
 * ponytail: rewrites the full file per change; move to Room if it passes ~10k transactions.
 */
class Store(dir: File, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(dir, "state.json"))
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load(dir))
    val state: StateFlow<AppState> = _state
    val value: AppState get() = _state.value

    /** Called after every change (widgets refresh, sync is queued). */
    var onChange: () -> Unit = {}

    /** [notify] false skips [onChange]; sync uses it to record its own progress without re-triggering itself. */
    fun update(notify: Boolean = true, f: (AppState) -> AppState) {
        _state.update(f)
        scope.launch(Dispatchers.IO) { writeLock.withLock { write(_state.value) } }
        if (notify) onChange()
    }

    private fun write(s: AppState) {
        var out: java.io.FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(json.encodeToString(AppState.serializer(), s).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            out?.let { file.failWrite(it) }
            Log.e("Store", "save failed", e)
        }
    }

    private fun load(dir: File): AppState {
        if (!file.baseFile.exists()) return AppState()
        return try {
            json.decodeFromString(AppState.serializer(), String(file.readFully()))
        } catch (e: Exception) {
            // Never overwrite data we couldn't read: keep the file aside and start empty.
            Log.e("Store", "load failed, keeping a copy", e)
            file.baseFile.copyTo(File(dir, "state.unreadable-${System.currentTimeMillis()}.json"))
            AppState()
        }
    }
}
