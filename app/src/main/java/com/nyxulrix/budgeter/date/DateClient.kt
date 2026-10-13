package com.nyxulrix.budgeter.date

import android.content.pm.PackageManager
import android.os.Build
import com.nyxulrix.budgeter.App
import com.nyxulrix.budgeter.BuildConfig
import com.nyxulrix.budgeter.ai.Secrets
import com.nyxulrix.budgeter.core.Step
import com.nyxulrix.budgeter.core.Tx
import com.nyxulrix.budgeter.core.budgetRows
import com.nyxulrix.budgeter.core.steps
import com.nyxulrix.budgeter.core.totals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

/*
 * The shared date budget lives in DateBudget's Firebase project (github.com/nyx-ulrix/DateBudget), so a partner on the
 * DateBudget site and Budgeter see the same budget. Sign-in: a Google ID token for Budgeter's own web client (safelisted
 * in DateBudget's Firebase Auth) is exchanged for a Firebase session over REST. Data: Firestore REST, refreshed every
 * few seconds while the screen is open instead of a live listener (no SDK). Port of the web app's src/date/client.ts.
 * Layout (see DateBudget's firestore.rules): budgets/{code} {members, names, plan}; budgets/{code}/tx/{id}.
 */
// DateBudget's Android-restricted API key, from keystore.properties (dateBudgetApiKey) or DATEBUDGET_API_KEY at build time.
private val KEY = BuildConfig.DATEBUDGET_API_KEY
private const val DOCS = "https://firestore.googleapis.com/v1/projects/datebudget/databases/(default)/documents"
private const val SESSION = "datebudget-session"                   // refresh token, encrypted (Secrets)

data class Budget(val code: String, val members: List<String>, val names: Map<String, String>, val plan: List<Step>)

enum class Phase { SignedOut, Loading, NoBudget, Ready }

data class DateState(
    val phase: Phase = Phase.SignedOut,
    val uid: String? = null,
    val name: String? = null,
    val budget: Budget? = null,
    val txs: List<Tx> = emptyList(),   // stored entries, newest first
    val all: List<Tx> = emptyList(),   // plus the plan's top-ups
    val left: Long = 0,
    val topped: Long = 0,
    val spent: Long = 0,
    val error: String? = null,
)

/** What a new or edited entry holds. */
data class TxDraft(val kind: String, val amount: Long, val note: String, val date: String)

object DateClient {
    private class Session(val idToken: String, val expires: Long, val refresh: String, val uid: String, val name: String)

    private val _state = MutableStateFlow(DateState())
    val state: StateFlow<DateState> = _state
    @Volatile private var session: Session? = null
    private fun set(f: DateState.() -> DateState) = _state.update(f)

    /** False in a build without the API key: the screen says so instead of offering sign-in. */
    val configured get() = KEY.isNotEmpty()

    // --- Firestore value conversion ---
    private fun enc(v: JsonElement): JsonObject = buildJsonObject {
        when {
            v is JsonNull -> put("nullValue", JsonNull)
            v is JsonPrimitive && v.isString -> put("stringValue", v.content)
            v is JsonPrimitive && v.booleanOrNull != null -> put("booleanValue", v.booleanOrNull)
            v is JsonPrimitive -> v.longOrNull?.let { put("integerValue", it.toString()) } ?: put("doubleValue", v.doubleOrNull)
            v is JsonArray -> put("arrayValue", buildJsonObject { put("values", JsonArray(v.map(::enc))) })
            else -> put("mapValue", buildJsonObject { put("fields", fields(v.jsonObject)) })
        }
    }
    private fun fields(o: JsonObject) = JsonObject(o.filterValues { it != JsonNull }.mapValues { enc(it.value) })
    private fun dec(v: JsonObject): JsonElement = when {
        "stringValue" in v -> v["stringValue"]!!
        "integerValue" in v -> JsonPrimitive(v["integerValue"]!!.jsonPrimitive.content.toLong())
        "doubleValue" in v -> v["doubleValue"]!!
        "booleanValue" in v -> v["booleanValue"]!!
        "arrayValue" in v -> JsonArray((v["arrayValue"]!!.jsonObject["values"] as? JsonArray).orEmpty().map { dec(it.jsonObject) })
        "mapValue" in v -> decFields(v["mapValue"]!!.jsonObject["fields"] as? JsonObject)
        else -> JsonNull
    }
    private fun decFields(f: JsonObject?) = JsonObject(f.orEmpty().mapValues { dec(it.value.jsonObject) })
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.long() = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }   // numbers, or "3600" as auth sends them

    /**
     * Date budget work runs at app level, not in the screen that started it: a screen that changes (signing in swaps
     * the sign-in panel for the budget) or is left mid-way would otherwise cancel the sign-in or save half-done.
     */
    private suspend fun <T> detached(block: suspend () -> T): T = App.scope.async(Dispatchers.IO) { block() }.await()

    // --- HTTP ---
    private class Res(val code: Int, val body: JsonElement?)

    private suspend fun http(method: String, url: String, body: String?, type: String, token: String? = null): Res = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            // Google APIs accept PATCH as a POST with an override header, which every HTTP stack supports.
            if (method == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") } else c.requestMethod = method
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("Content-Type", type)
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if ("?key=" in url) {   // the key only works from this app, so say which app is calling
                c.setRequestProperty("X-Android-Package", App.app.packageName)
                c.setRequestProperty("X-Android-Cert", cert)
            }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            Res(code, runCatching { Json.parseToJsonElement(text) }.getOrNull())
        } finally {
            c.disconnect()
        }
    }

    /** SHA-1 of the installed app's signing certificate, uppercase hex without colons, as Google's key check wants it. */
    private val cert: String by lazy {
        val pm = App.app.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(App.app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners.first().toByteArray()
        else @Suppress("DEPRECATION") pm.getPackageInfo(App.app.packageName, PackageManager.GET_SIGNATURES).signatures!!.first().toByteArray()
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
    }

    private fun Res.message() = ((body as? JsonObject)?.get("error") as? JsonObject)?.get("message").str()

    // --- auth ---
    private suspend fun post(url: String, body: String, form: Boolean = false): JsonObject {
        val r = http("POST", url, body, if (form) "application/x-www-form-urlencoded" else "application/json")
        if (r.code !in 200..299) throw Exception(r.message() ?: "DateBudget sign-in failed (${r.code})")
        return r.body as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun save(s: Session) = Secrets.put(App.app, SESSION, buildJsonObject { put("refresh", s.refresh); put("uid", s.uid); put("name", s.name) }.toString())

    private suspend fun token(): String {
        val s = session ?: throw Exception("Not signed in to the date budget.")
        if (s.expires > System.currentTimeMillis() + 60_000) return s.idToken
        val j = post("https://securetoken.googleapis.com/v1/token?key=$KEY", "grant_type=refresh_token&refresh_token=${URLEncoder.encode(s.refresh, "UTF-8")}", true)
        val next = Session(j["id_token"].str().orEmpty(), System.currentTimeMillis() + (j["expires_in"].long() ?: 0) * 1000,
            j["refresh_token"].str() ?: s.refresh, s.uid, s.name)
        session = next
        save(next)
        return next.idToken
    }

    /** Signs in with a Google ID token (from Sign in with Google). */
    suspend fun signIn(googleIdToken: String) = detached {
        set { copy(phase = Phase.Loading, error = null) }
        try {
            val j = post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$KEY", buildJsonObject {
                put("postBody", "id_token=$googleIdToken&providerId=google.com"); put("requestUri", "http://localhost"); put("returnSecureToken", true)
            }.toString())
            val s = Session(j["idToken"].str().orEmpty(), System.currentTimeMillis() + (j["expiresIn"].long() ?: 0) * 1000,
                j["refreshToken"].str().orEmpty(), j["localId"].str().orEmpty(),
                j["displayName"].str()?.ifEmpty { null } ?: j["firstName"].str()?.ifEmpty { null } ?: "Me")
            session = s
            save(s)
            refresh()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            set { copy(phase = Phase.SignedOut, error = e.message ?: "Sign-in failed.") }
        }
    }

    fun signOut() {
        session = null
        Secrets.remove(App.app, SESSION)
        _state.value = DateState()
    }

    /** On app start: picks up the saved session, if any. */
    suspend fun resume() = detached {
        val saved = Secrets.get(App.app, SESSION) ?: return@detached
        try {
            val s = Json.parseToJsonElement(saved).jsonObject
            session = Session("", 0, s["refresh"].str()!!, s["uid"].str()!!, s["name"].str().orEmpty())
            set { copy(phase = Phase.Loading) }
            refresh()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            set { copy(phase = Phase.SignedOut) }
        }
    }

    // --- data ---
    private suspend fun api(method: String, path: String, body: JsonElement? = null): JsonElement {
        val r = http(method, if (path.startsWith("http")) path else "$DOCS$path", body?.toString(), "application/json", token())
        if (r.code !in 200..299) throw Exception(if (r.code == 403) "You don't have access to that budget." else r.message() ?: "DateBudget error ${r.code}")
        return r.body ?: JsonObject(emptyMap())
    }

    /** Reloads the budget and its entries. */
    suspend fun refresh() = detached {
        val s = session ?: return@detached
        try {
            val q = api("POST", ":runQuery", Json.parseToJsonElement("""{"structuredQuery":{"from":[{"collectionId":"budgets"}],
                "where":{"fieldFilter":{"field":{"fieldPath":"members"},"op":"ARRAY_CONTAINS","value":{"stringValue":${JsonPrimitive(s.uid)}}}},"limit":1}}"""))
            val doc = (q as? JsonArray).orEmpty().firstNotNullOfOrNull { (it as? JsonObject)?.get("document") as? JsonObject }
            if (doc == null) {
                set { copy(phase = Phase.NoBudget, uid = s.uid, name = s.name, budget = null, txs = emptyList(), all = emptyList(), error = null) }
                return@detached
            }
            val f = decFields(doc["fields"] as? JsonObject)
            val code = doc["name"].str().orEmpty().substringAfterLast('/')
            val budget = Budget(
                code,
                (f["members"] as? JsonArray).orEmpty().mapNotNull { it.str() },
                (f["names"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> v.str()?.let { k to it } }.toMap(),
                steps(f["plan"]?.takeIf { it != JsonNull } ?: f["monthly"]),
            )
            val txs = mutableListOf<Tx>()
            var page = ""
            do {
                val r = api("GET", "/budgets/$code/tx?pageSize=300" + if (page.isNotEmpty()) "&pageToken=${URLEncoder.encode(page, "UTF-8")}" else "").jsonObject
                (r["documents"] as? JsonArray).orEmpty().forEach { d ->
                    val x = decFields(d.jsonObject["fields"] as? JsonObject)
                    txs += Tx(d.jsonObject["name"].str().orEmpty().substringAfterLast('/'), x["kind"].str().orEmpty(), x["amount"].long() ?: 0,
                        x["note"].str().orEmpty(), x["date"].str().orEmpty(), x["by"].str().orEmpty())
                }
                page = r["nextPageToken"].str().orEmpty()
            } while (page.isNotEmpty())
            // DateBudget's carry-over for budgets from its first version: a fixed starting amount counts as a top-up.
            val base = f["base"]?.takeIf { it is JsonPrimitive && !it.isString }.long()
            if (base != null && base > 0 && txs.none { it.id == "starting-budget" }) {
                txs += Tx("starting-budget", "topup", base, "Starting budget", today(), "")
            }
            txs.sortByDescending { it.date }
            val all = (txs + budgetRows(budget.plan, today())).sortedByDescending { it.date }
            val t = totals(all)
            set { copy(phase = Phase.Ready, uid = s.uid, name = s.name, budget = budget, txs = txs.toList(), all = all, left = t.left, topped = t.topped, spent = t.spent, error = null) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            set { copy(error = e.message ?: "Couldn't load the date budget.") }
        }
    }

    private fun today() = LocalDate.now().toString()
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // no 0/O or 1/I lookalikes, as in DateBudget
    private fun newCode() = ByteArray(8).also { SecureRandom().nextBytes(it) }.map { ALPHABET[(it.toInt() and 0xFF) % 32] }.joinToString("")

    private fun me() = session ?: throw Exception("Not signed in to the date budget.")
    private fun code() = _state.value.budget?.code ?: throw Exception("No date budget yet.")

    /** Starts a new shared budget with you as its only member; the code is what your partner enters. */
    suspend fun create() = detached {
        val s = me()
        api("POST", "/budgets?documentId=${newCode()}", buildJsonObject {
            put("fields", fields(buildJsonObject {
                put("members", JsonArray(listOf(JsonPrimitive(s.uid))))
                put("names", buildJsonObject { put(s.uid, s.name) })
            }))
        })
        refresh()
    }

    /** Joins your partner's budget with their code (as DateBudget does: add yourself to members and names). */
    suspend fun join(code: String) = detached {
        val c = code.trim().uppercase()
        try {
            val s = me()
            val sv = { v: String -> buildJsonObject { put("stringValue", v) } }
            api("POST", ":commit", buildJsonObject {
                put("writes", JsonArray(listOf(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", "projects/datebudget/databases/(default)/documents/budgets/$c")
                        put("fields", buildJsonObject { put("names", buildJsonObject { put("mapValue", buildJsonObject { put("fields", buildJsonObject { put(s.uid, sv(s.name)) }) }) }) })
                    })
                    put("updateMask", buildJsonObject { put("fieldPaths", JsonArray(listOf(JsonPrimitive("names.`${s.uid}`")))) })
                    put("updateTransforms", JsonArray(listOf(buildJsonObject {
                        put("fieldPath", "members")
                        put("appendMissingElements", buildJsonObject { put("values", JsonArray(listOf(sv(s.uid)))) })
                    })))
                    put("currentDocument", buildJsonObject { put("exists", true) })
                })))
            })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw Exception("That code didn't work. Check it, or the budget may already have two people.")
        }
        refresh()
    }

    private fun planJson(plan: List<Step>) = JsonArray(plan.map { s ->
        buildJsonObject { put("from", s.from); put("amount", s.amount); put("every", s.every.id); s.day?.let { put("day", it) }
            s.split?.let { put("split", JsonObject(it.mapValues { (_, v) -> JsonPrimitive(v) })) } }
    })

    suspend fun setPlan(plan: List<Step>) = detached {
        api("PATCH", "/budgets/${code()}?updateMask.fieldPaths=plan", buildJsonObject { put("fields", fields(buildJsonObject { put("plan", planJson(plan)) })) })
        refresh()
    }

    private fun txFields(d: TxDraft, by: String? = null) = fields(buildJsonObject {
        put("kind", d.kind); put("amount", d.amount); put("note", d.note); put("date", d.date); by?.let { put("by", it) }
    })

    suspend fun addTx(d: TxDraft) = detached {
        val s = me()
        api("POST", "/budgets/${code()}/tx", buildJsonObject { put("fields", txFields(d, s.uid)) })
        refresh()
    }

    suspend fun updateTx(id: String, d: TxDraft) = detached {
        api("PATCH", "/budgets/${code()}/tx/$id?updateMask.fieldPaths=kind&updateMask.fieldPaths=amount&updateMask.fieldPaths=note&updateMask.fieldPaths=date",
            buildJsonObject { put("fields", txFields(d)) })
        refresh()
    }

    suspend fun deleteTx(id: String) = detached {
        api("DELETE", "/budgets/${code()}/tx/$id")
        refresh()
    }
}
