package com.nyxulrix.budgeter.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nyxulrix.budgeter.BuildConfig
import com.nyxulrix.budgeter.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from GitHub Releases: read the latest release, download its APK, hand it to Android's installer.
 * Android only accepts it if it's signed with the same key as the installed app, so a tampered APK can't install.
 */
object Updater {
    data class Release(val version: String, val apkUrl: String, val notes: String, val size: Long)

    val available = MutableStateFlow<Release?>(null)
    val status = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** GitHub's API, or the local test server in debug builds when one is set in Profile → Updates. */
    fun server(ctx: Context): String = prefs(ctx).getString("server", null)?.takeIf { BuildConfig.DEBUG && it.isNotBlank() } ?: "https://api.github.com"
    fun testServer(ctx: Context): String = prefs(ctx).getString("server", "").orEmpty()
    fun setTestServer(ctx: Context, url: String) = prefs(ctx).edit().putString("server", url.trim().trimEnd('/')).putLong("checked", 0).apply()

    /** True when [a] is a higher version than [b] ("0.10.0" > "0.9.3"). */
    fun newer(a: String, b: String): Boolean {
        fun parts(s: String) = s.removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c > 0
        }
        return false
    }

    /** Looks for a newer release, at most every 6 hours unless [force]. Never throws. */
    suspend fun check(ctx: Context, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val p = prefs(ctx)
        if (!force && System.currentTimeMillis() - p.getLong("checked", 0) < 6 * 3600_000L) return@withContext available.value
        runCatching {
            val c = URL("${server(ctx)}/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Budgeter/${BuildConfig.VERSION_NAME}")
            if (c.responseCode == 404) { p.edit().putLong("checked", System.currentTimeMillis()).apply(); return@runCatching null }
            if (c.responseCode !in 200..299) error("Update server said ${c.responseCode}")
            val o = json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
            p.edit().putLong("checked", System.currentTimeMillis()).apply()
            val tag = o["tag_name"]!!.jsonPrimitive.content
            val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return@runCatching null
            Release(tag.removePrefix("v"), apk["browser_download_url"]!!.jsonPrimitive.content,
                o["body"]?.jsonPrimitive?.content.orEmpty(), apk["size"]?.jsonPrimitive?.long ?: -1)
                .takeIf { newer(it.version, BuildConfig.VERSION_NAME) }
        }.onSuccess { available.value = it; if (force) status.value = if (it == null) "You're on the latest version." else null }
            .onFailure { if (force) status.value = "Couldn't check for updates: ${it.message}" }
            .getOrNull()
    }

    /** Downloads [r] and opens the system installer. Asks for the "install unknown apps" permission first if needed. */
    suspend fun install(ctx: Context, r: Release) {
        if (busy.value) return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            status.value = "Allow Budgeter to install updates, then tap Install again."
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        busy.value = true
        try {
            val file = withContext(Dispatchers.IO) { download(ctx, r) }
            if (!sameSigner(ctx, file)) { file.delete(); status.value = "That update isn't signed with this app's key, so it was not installed."; return }
            status.value = "Opening installer…"
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            status.value = "Update failed: ${e.message}"
        } finally {
            busy.value = false
        }
    }

    private fun download(ctx: Context, r: Release): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "budgeter-${r.version}.apk")
        val c = URL(r.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Budgeter/${BuildConfig.VERSION_NAME}")
        if (c.responseCode !in 200..299) error("download said ${c.responseCode}")
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L; var lastPct = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) { val pct = (done * 100 / total).toInt(); if (pct != lastPct) { lastPct = pct; status.value = "Downloading $pct%" } }
                }
            }
        }
        if (r.size > 0 && out.length() != r.size) { out.delete(); error("download was incomplete") }
        return out
    }

    /** Same signing certificate as the installed app (Android 9+; older versions rely on the installer's own check). */
    private fun sameSigner(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val pm = ctx.packageManager
        val mine = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.map { it.toCharsString() }
        val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }
        return mine != null && mine == theirs
    }
}
