package com.nyxulrix.budgeter.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.nyxulrix.budgeter.data.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * "Sign in with OpenRouter" (OAuth PKCE). OpenRouter only redirects to web or localhost URLs, so the app listens once
 * on a localhost port, the browser lands there after sign-in, and we swap the code for a key the user controls.
 * If the redirect can't land (some browsers block it), the user can sign in headless and paste the code shown.
 */
object OpenRouterLogin {
    val status = MutableStateFlow<String?>(null)
    private var verifier = ""
    private var server: ServerSocket? = null

    fun start(ctx: Context) {
        verifier = Base64.encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        server?.close()
        val s = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = 10 * 60 * 1000 }
        server = s
        val app = ctx.applicationContext
        thread(name = "openrouter-callback", isDaemon = true) {
            runCatching {
                while (!s.isClosed) s.accept().use { sock ->                // stray local connections are ignored; wait for the callback
                    sock.soTimeout = 15_000
                    val line = sock.getInputStream().bufferedReader().readLine().orEmpty()     // GET /callback?code=... HTTP/1.1
                    val uri = Uri.parse("http://localhost" + line.split(" ").getOrElse(1) { "/" })
                    if (uri.path != "/callback") return@use
                    val code = uri.getQueryParameter("code")
                    val page = if (code != null) "Signed in. Return to Budgeter." else "Sign-in was cancelled."
                    val html = "<html><body style='font-family:monospace;background:#E7D6AD;padding:24px'><h2>$page</h2>" +
                        "<p><a href='budgeter://openrouter'>Open Budgeter</a></p></body></html>"
                    sock.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n$html").toByteArray())
                    if (code != null) exchange(app, code) else status.value = "Sign-in cancelled."
                    s.close()
                }
            }.onFailure { if (status.value == "Waiting for sign-in…") status.value = "Sign-in timed out. Try again or paste the code." }
            s.close()
        }
        status.value = "Waiting for sign-in…"
        open(ctx, "https://openrouter.ai/auth?callback_url=http://localhost:${s.localPort}/callback&code_challenge=${challenge()}&code_challenge_method=S256")
    }

    /** Headless flow: OpenRouter shows a code on screen instead of redirecting. */
    fun startHeadless(ctx: Context) {
        verifier = Base64.encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        status.value = "Copy the code OpenRouter shows, then paste it here."
        open(ctx, "https://openrouter.ai/auth?code_challenge=${challenge()}&code_challenge_method=S256&key_label=Budgeter")
    }

    /** Called when the browser's "Open Budgeter" link brings us back; the key exchange already ran in the listener. */
    fun finish(ctx: Context, uri: Uri) {
        uri.getQueryParameter("code")?.let { code -> thread { exchange(ctx.applicationContext, code) } }
    }

    fun pasteCode(ctx: Context, code: String) = thread { exchange(ctx.applicationContext, code.trim()) }

    private fun challenge(): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun open(ctx: Context, url: String) =
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun exchange(ctx: Context, code: String) {
        status.value = "Finishing sign-in…"
        runCatching {
            val c = URL("https://openrouter.ai/api/v1/auth/keys").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use {
                it.write(buildJsonObject { put("code", code); put("code_verifier", verifier); put("code_challenge_method", "S256") }.toString().toByteArray())
            }
            if (c.responseCode !in 200..299) error("OpenRouter said ${c.responseCode}. Try signing in again.")
            val key = json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject["key"]!!.jsonPrimitive.content
            val p = preset("openrouter")
            Ai.save(ctx, Provider(preset = p.id, label = "OpenRouter", base = p.base, model = p.model), key)
        }.onSuccess { status.value = "Signed in to OpenRouter." }
            .onFailure { status.value = it.message ?: "Sign-in failed." }
    }
}
