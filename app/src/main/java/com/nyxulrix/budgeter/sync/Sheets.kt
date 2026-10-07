package com.nyxulrix.budgeter.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.core.plain
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.currency
import com.nyxulrix.budgeter.data.json
import com.nyxulrix.budgeter.data.periodOf
import com.nyxulrix.budgeter.data.personLabel
import com.nyxulrix.budgeter.data.setSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Only lets the app see files it created itself, so Google treats it as non-sensitive. */
const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

val authRequest: AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE))).build()

class HttpError(val code: Int, msg: String) : Exception(msg)

/** Export of transactions to one Google spreadsheet, one tab per budget period. Export only. */
object Sheets {
    private val HEADER = listOf("ID", "Date", "Name", "Category", "Amount", "My Share", "Tax", "Service Charge",
        "Paid Currency", "Paid Amount", "Payer", "Split Method", "People", "Trip", "Items JSON", "Notes")
    private const val LAST_COL = "P"

    fun needsSync(t: Txn) = t.syncedAt == null || t.updatedAt > t.syncedAt

    private val lock = kotlinx.coroutines.sync.Mutex()

    /**
     * Pushes every new, changed or deleted transaction. Throws [HttpError] on API errors.
     * One run at a time: a replaced worker can't be interrupted mid-HTTP, so a second run waits instead of
     * racing it and appending the same rows twice.
     */
    suspend fun run(token: String) = lock.withLock { runLocked(token) }

    private fun runLocked(token: String) {
        val store = App.store
        var st = store.value
        var sheetId = st.sync.spreadsheetId ?: createSpreadsheet(token).also { id -> store.update(false) { it.copy(sync = it.sync.copy(spreadsheetId = id)) } }
        val tabs = try {
            existingTabs(token, sheetId).toMutableSet()
        } catch (e: HttpError) {
            if (e.code != 404) throw e
            // The spreadsheet was deleted: start a fresh one and push everything again.
            val fresh = createSpreadsheet(token)
            sheetId = fresh
            store.update(false) { s -> s.copy(sync = s.sync.copy(spreadsheetId = fresh), txns = s.txns.map { it.copy(syncedAt = null, syncedTab = null) }) }
            mutableSetOf()
        }
        st = store.value
        val todo = st.txns.filter(::needsSync)
        if (todo.isEmpty()) {
            store.update(false) { it.copy(sync = it.sync.copy(lastSync = System.currentTimeMillis(), paused = null)) }
            return
        }
        val now = System.currentTimeMillis()
        val done = mutableMapOf<String, String?>()   // txn id → tab written (null = deleted)

        // Rows to clear: deletions, and rows whose date moved them to another tab.
        val clears = todo.filter { it.syncedTab != null && (it.deleted || it.syncedTab != tabOf(st, it)) }
        for ((tab, list) in clears.groupBy { it.syncedTab!! }) {
            if (tab !in tabs) continue
            val rows = idRows(token, sheetId, tab)
            list.mapNotNull { rows[it.id] }.forEach { r -> clear(token, sheetId, "'$tab'!A$r:$LAST_COL$r") }
        }
        todo.filter { it.deleted }.forEach { done[it.id] = null }

        for ((tab, list) in todo.filter { !it.deleted }.groupBy { tabOf(st, it) }) {
            if (tab !in tabs) { addTab(token, sheetId, tab); tabs += tab }
            val rows = idRows(token, sheetId, tab)
            val (update, append) = list.partition { it.id in rows }
            if (update.isNotEmpty()) batchUpdate(token, sheetId, update.map { "'$tab'!A${rows[it.id]}:$LAST_COL${rows[it.id]}" to row(st, it) })
            if (append.isNotEmpty()) append(token, sheetId, tab, append.map { row(st, it) })
            list.forEach { done[it.id] = tab }
        }

        store.update(false) { s ->
            s.copy(
                txns = s.txns.mapNotNull { t ->
                    when {
                        t.id !in done -> t
                        t.updatedAt > todo.first { it.id == t.id }.updatedAt -> t        // edited or deleted mid-sync: next run handles it
                        t.deleted -> null                                                 // removal reached Sheets: purge
                        else -> t.copy(syncedAt = now, syncedTab = done[t.id])
                    }
                },
                sync = s.sync.copy(lastSync = now, paused = null),
            )
        }
    }

    private fun tabOf(st: AppState, t: Txn) = st.periodOf(LocalDate.parse(t.date)).key

    private fun row(st: AppState, t: Txn): List<JsonElement> {
        val home = st.currency
        val paidCur = t.foreign?.currency ?: home
        fun n(minor: Long, cur: String) = JsonPrimitive(plain(minor, cur).toBigDecimal())
        val items = buildJsonArray {
            t.items.forEach { i -> add(buildJsonObject { put("name", i.name); put("price", plain(i.price, paidCur)); put("cost", plain(i.cost, home)); put("category", i.category) }) }
        }
        return listOf(
            JsonPrimitive(t.id), JsonPrimitive(t.date), JsonPrimitive(t.merchant), JsonPrimitive(t.category),
            n(t.total, home), n(t.myShare, home), n(t.tax, paidCur), n(t.serviceCharge, paidCur),
            JsonPrimitive(paidCur), n(t.foreign?.amount ?: t.total, paidCur),
            JsonPrimitive(personLabel(t.payer)), JsonPrimitive(if (t.shares.isEmpty()) "none" else t.method.name.lowercase()),
            JsonPrimitive(maxOf(t.people, 1)),
            JsonPrimitive(st.trips.firstOrNull { it.id == t.tripId }?.name ?: ""),
            JsonPrimitive(if (t.items.isEmpty()) "" else items.toString()), JsonPrimitive(t.note),
        )
    }

    // Sheets REST calls. RAW input: text never becomes a formula, numbers stay numbers.

    private const val API = "https://sheets.googleapis.com/v4/spreadsheets"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun createSpreadsheet(token: String): String {
        val body = buildJsonObject { putJsonObject("properties") { put("title", "Budgeter") } }
        return json.parseToJsonElement(http("POST", API, token, body.toString())).jsonObject["spreadsheetId"]!!.jsonPrimitive.content
    }

    private fun existingTabs(token: String, id: String): Set<String> =
        json.parseToJsonElement(http("GET", "$API/$id?fields=sheets.properties.title", token, null)).jsonObject["sheets"]?.jsonArray
            ?.map { it.jsonObject["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content }?.toSet() ?: emptySet()

    private fun addTab(token: String, id: String, tab: String) {
        val body = buildJsonObject { putJsonArray("requests") { add(buildJsonObject { putJsonObject("addSheet") { putJsonObject("properties") { put("title", tab) } } }) } }
        http("POST", "$API/$id:batchUpdate", token, body.toString())
        batchUpdate(token, id, listOf("'$tab'!A1:${LAST_COL}1" to HEADER.map { JsonPrimitive(it) }))
    }

    /** Transaction id → sheet row number, read from column A. */
    private fun idRows(token: String, id: String, tab: String): Map<String, Int> {
        val o = json.parseToJsonElement(http("GET", "$API/$id/values/${enc("'$tab'!A:A")}", token, null)).jsonObject
        return (o["values"] as? JsonArray).orEmpty().mapIndexedNotNull { i, r -> r.jsonArray.firstOrNull()?.jsonPrimitive?.content?.let { it to i + 1 } }.toMap()
    }

    private fun batchUpdate(token: String, id: String, data: List<Pair<String, List<JsonElement>>>) {
        val body = buildJsonObject {
            put("valueInputOption", "RAW")
            putJsonArray("data") { data.forEach { (range, row) -> add(buildJsonObject { put("range", range); put("values", JsonArray(listOf(JsonArray(row)))) }) } }
        }
        http("POST", "$API/$id/values:batchUpdate", token, body.toString())
    }

    private fun append(token: String, id: String, tab: String, rows: List<List<JsonElement>>) {
        val body = buildJsonObject { put("values", JsonArray(rows.map { JsonArray(it) })) }
        http("POST", "$API/$id/values/${enc("'$tab'!A1")}:append?valueInputOption=RAW&insertDataOption=INSERT_ROWS", token, body.toString())
    }

    private fun clear(token: String, id: String, range: String) = http("POST", "$API/$id/values/${enc(range)}:clear", token, "{}")

    private fun http(method: String, url: String, token: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpError(code, "Google Sheets error $code: ${text.take(200)}")
            return text
        } finally {
            c.disconnect()
        }
    }
}

/** Background sync: the fallback when the direct push in [SyncWorker.queue] can't finish (offline, app closed). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = syncOnce(applicationContext, runAttemptCount)

    companion object {
        private var direct: Job? = null

        /** One sync attempt. Sheets.run holds a lock, so the direct push and the worker never write the same rows twice. */
        suspend fun syncOnce(ctx: Context, attempt: Int): Result {
            if (!App.store.value.sync.enabled) return Result.success()
            fun pause(reason: String): Result {
                App.store.update(false) { it.copy(sync = it.sync.copy(paused = reason)) }
                return Result.success()
            }
            val auth: AuthorizationResult = try {
                Identity.getAuthorizationClient(ctx).authorize(authRequest).await()
            } catch (e: Exception) {
                return pause("Google sign-in failed: ${e.message}")
            }
            if (auth.hasResolution()) return pause("Google needs you to sign in again.")
            val token = auth.accessToken ?: return pause("Google gave no access token.")
            return try {
                Sheets.run(token)
                Result.success()
            } catch (e: HttpError) {
                when (e.code) {
                    401, 403 -> pause("Google access was revoked or expired. Sign in again.")
                    429, in 500..599 -> if (attempt < 8) Result.retry() else pause("Google Sheets keeps failing. Will retry on the next change.")
                    else -> pause(e.message ?: "Sync failed.")
                }
            } catch (e: IOException) {
                Result.retry()
            } catch (e: Exception) {
                pause("Sync failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        /**
         * Called after every change. Pushes to the sheet a couple of seconds later (so a burst of edits goes up together)
         * straight from the app, and also queues the worker, which picks up anything that didn't make it.
         */
        fun queue(ctx: Context, delaySeconds: Long = 5) {
            if (!App.store.value.sync.enabled) return
            val app = ctx.applicationContext
            direct?.cancel()
            direct = App.scope.launch(Dispatchers.IO) { delay(2_000); syncOnce(app, 0) }
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork("sheets-sync", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** Turns sync on after an interactive Google authorization. */
fun enableSync(account: String?) {
    App.store.setSync { it.copy(enabled = true, paused = null, account = account ?: it.account) }
    SyncWorker.queue(App.app, 0)
}
