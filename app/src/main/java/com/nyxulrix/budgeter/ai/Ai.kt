package com.nyxulrix.budgeter.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nyxulrix.budgeter.core.ParsedReceipt
import com.nyxulrix.budgeter.core.ReceiptItem
import com.nyxulrix.budgeter.core.ReceiptText
import com.nyxulrix.budgeter.core.digits
import com.nyxulrix.budgeter.data.json
import com.nyxulrix.budgeter.data.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A provider preset. Every one speaks the OpenAI-style `POST {base}/chat/completions` with a Bearer key,
 * so one client covers them all.
 */
data class Preset(val id: String, val name: String, val base: String, val model: String, val keyUrl: String, val note: String)

val PRESETS = listOf(
    Preset("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash",
        "https://aistudio.google.com/apikey", "Free key from Google AI Studio. Cheapest way to start."),
    Preset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto",
        "https://openrouter.ai/keys", "Sign in once to reach Claude, Gemini, GPT, DeepSeek, Perplexity and free models."),
    Preset("claude", "Claude", "https://api.anthropic.com/v1", "claude-haiku-4-5",
        "https://console.anthropic.com/settings/keys", "Anthropic API key."),
    Preset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-5-mini",
        "https://platform.openai.com/api-keys", "OpenAI API key."),
    Preset("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat",
        "https://platform.deepseek.com/api_keys", "DeepSeek API key. Very cheap."),
    Preset("perplexity", "Perplexity", "https://api.perplexity.ai", "sonar",
        "https://www.perplexity.ai/account/api/keys", "Perplexity API key."),
    Preset("custom", "Custom", "", "", "", "Any OpenAI-compatible server: Ollama, LM Studio, a proxy."),
)

fun preset(id: String) = PRESETS.first { it.id == id || it.id == "custom" }

/** A saved login. The key itself lives encrypted in [Secrets], never in this object. */
@Serializable
data class Provider(
    val id: String = newId(),
    val preset: String,
    val label: String,
    val base: String,
    val model: String,
)

object Ai {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ai", Context.MODE_PRIVATE)

    fun providers(ctx: Context): List<Provider> = prefs(ctx).getString("providers", null)
        ?.let { runCatching { json.decodeFromString(ListSerializer(Provider.serializer()), it) }.getOrNull() } ?: emptyList()

    fun activeId(ctx: Context): String? = prefs(ctx).getString("active", null)?.takeIf { id -> providers(ctx).any { it.id == id } }
    fun active(ctx: Context): Provider? = activeId(ctx)?.let { id -> providers(ctx).firstOrNull { it.id == id } }
    fun setActive(ctx: Context, id: String?) = prefs(ctx).edit().putString("active", id).apply()

    fun save(ctx: Context, p: Provider, key: String?) {
        val list = providers(ctx).filterNot { it.id == p.id } + p
        prefs(ctx).edit().putString("providers", json.encodeToString(ListSerializer(Provider.serializer()), list)).apply()
        if (key != null) Secrets.put(ctx, "key_${p.id}", key.trim())
        if (activeId(ctx) == null) setActive(ctx, p.id)
    }

    fun remove(ctx: Context, id: String) {
        prefs(ctx).edit().putString("providers", json.encodeToString(ListSerializer(Provider.serializer()), providers(ctx).filterNot { it.id == id })).apply()
        Secrets.remove(ctx, "key_$id")
        if (activeId(ctx) == null) setActive(ctx, providers(ctx).firstOrNull()?.id)
    }

    fun hasKey(ctx: Context, p: Provider) = Secrets.get(ctx, "key_${p.id}") != null

    private const val SYSTEM = """You read receipt text produced by OCR and return JSON only, no prose, no code fences.
Schema:
{"merchant": string, "date": "YYYY-MM-DD" or null, "currency": ISO 4217 code or null,
 "items": [{"name": string, "qty": integer, "price": number}],
 "discount": number, "subtotal": number or null, "service_charge": number, "tax": number,
 "tax_included_in_prices": boolean, "total": number or null}
Rules:
- Copy only values that are printed. Never calculate, estimate or invent a tax, GST, VAT or service charge. If one is not printed, use 0.
- "price" is the printed line total for that item (quantity already multiplied).
- A discount printed against one item is its own item with a negative price. A discount on the whole bill goes in "discount" as a positive number.
- If the receipt says prices include tax (e.g. "inclusive of GST"), set tax_included_in_prices true and still copy the printed tax amount.
- If a service charge is already inside item prices and not printed as its own line, service_charge is 0.
- Ignore payment, card, cash, change, rounding and loyalty-point lines.
- Numbers are plain decimals without currency symbols or thousands separators.
- Dates on receipts are usually day-first unless that is impossible."""

    @Serializable
    private data class Dto(
        val merchant: String? = null,
        val date: String? = null,
        val currency: String? = null,
        val items: List<ItemDto> = emptyList(),
        val discount: Double? = null,
        val subtotal: Double? = null,
        @SerialName("service_charge") val serviceCharge: Double? = null,
        val tax: Double? = null,
        @SerialName("tax_included_in_prices") val taxIncluded: Boolean? = null,
        val total: Double? = null,
    )

    @Serializable
    private data class ItemDto(val name: String = "", val qty: Int? = null, val price: Double = 0.0)

    /**
     * Turns OCR text into a [ParsedReceipt] with [p], or with the built-in rules when [p] is null.
     * Only text is sent; images never leave the phone. Throws with a readable message on AI failure.
     */
    suspend fun parse(ctx: Context, p: Provider?, text: String, fallbackCurrency: String): ParsedReceipt {
        if (p == null) return ReceiptText.parse(text, fallbackCurrency)
        val reply = chat(ctx, p, SYSTEM, text)
        val obj = reply.substring(reply.indexOf('{').coerceAtLeast(0), (reply.lastIndexOf('}') + 1).coerceAtLeast(0))
        val d = runCatching { json.decodeFromString(Dto.serializer(), obj) }.getOrElse { throw Exception("${p.label} didn't return receipt data. Try again or use the built-in reader.") }
        val cur = d.currency?.uppercase()?.takeIf { it.length == 3 } ?: fallbackCurrency
        fun m(v: Double?) = v?.let { BigDecimal.valueOf(it).movePointRight(digits(cur)).setScale(0, RoundingMode.HALF_UP).toLong() }
        return ParsedReceipt(
            merchant = d.merchant.orEmpty(),
            date = d.date?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) },
            currency = d.currency?.uppercase()?.takeIf { it.length == 3 },
            items = d.items.map { ReceiptItem(it.name, m(it.price) ?: 0, (it.qty ?: 1).coerceAtLeast(1)) },
            discount = kotlin.math.abs(m(d.discount) ?: 0),
            subtotal = m(d.subtotal),
            serviceCharge = m(d.serviceCharge) ?: 0,
            tax = m(d.tax) ?: 0,
            taxIncluded = d.taxIncluded ?: false,
            total = m(d.total),
        )
    }

    /** One chat completion; returns the reply text. */
    suspend fun chat(ctx: Context, p: Provider, system: String, user: String): String = withContext(Dispatchers.IO) {
        val key = Secrets.get(ctx, "key_${p.id}") ?: throw Exception("${p.label} has no key saved. Add it in Profile → AI.")
        val body = buildJsonObject {
            put("model", p.model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val res = http("POST", p.base.trimEnd('/') + "/chat/completions", key, p, body.toString())
        runCatching {
            json.parseToJsonElement(res).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrElse { throw Exception("${p.label} sent an unexpected reply.") }
    }

    /** Model ids the provider offers, for the model picker. */
    suspend fun models(ctx: Context, p: Provider): List<String> = withContext(Dispatchers.IO) {
        val key = Secrets.get(ctx, "key_${p.id}") ?: return@withContext emptyList()
        val res = http("GET", p.base.trimEnd('/') + "/models", key, p, null)
        runCatching {
            (json.parseToJsonElement(res).jsonObject["data"]!!.jsonArray).map { it.jsonObject["id"]!!.jsonPrimitive.content.removePrefix("models/") }.sorted()
        }.getOrDefault(emptyList())
    }

    private fun http(method: String, url: String, key: String, p: Provider, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 90_000
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json")
            if (p.preset == "claude") { c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01") }
            if (p.preset == "openrouter") { c.setRequestProperty("X-Title", "Budgeter") }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception(friendly(p, code, text))
            return text
        } catch (e: java.io.IOException) {
            throw Exception("Couldn't reach ${p.label}. Check your connection.")
        } finally {
            c.disconnect()
        }
    }

    private fun friendly(p: Provider, code: Int, text: String): String {
        val msg = runCatching {
            val o = json.parseToJsonElement(text) as JsonObject
            (o["error"]?.let { e -> (e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: e.jsonPrimitive.content })
        }.getOrNull() ?: text.take(200)
        return when (code) {
            401, 403 -> "${p.label} rejected the key. Check it in Profile → AI. ($msg)"
            402 -> "${p.label} account is out of credit. ($msg)"
            404 -> "${p.label} doesn't know model \"${p.model}\". Pick another in Profile → AI."
            429 -> "${p.label} rate limit hit. Wait a minute and retry."
            else -> "${p.label} error $code: $msg"
        }
    }
}

/** Small values encrypted with an Android Keystore AES key that never leaves the phone's secure hardware. */
object Secrets {
    private const val ALIAS = "budgeter-secrets"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun put(ctx: Context, name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(value.toByteArray())
        prefs(ctx).edit().putString(name, b64(c.iv) + ":" + b64(enc)).apply()
    }

    /** Null if missing or unreadable (e.g. restored from a backup to a new phone, where the key doesn't exist). */
    fun get(ctx: Context, name: String): String? = runCatching {
        val (iv, enc) = prefs(ctx).getString(name, null)!!.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(enc))
    }.getOrNull()

    fun remove(ctx: Context, name: String) = prefs(ctx).edit().remove(name).apply()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
