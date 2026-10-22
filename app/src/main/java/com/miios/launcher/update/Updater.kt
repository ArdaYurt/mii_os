package com.miios.launcher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates Mii_OS from its own GitHub release, so the box never needs a laptop for an update.
 * Every build on main publishes mii-os.apk and version.json to the "latest" release; Mii_OS
 * compares version.json's versionCode with its own and offers the newer build.
 */
object Updater {

    private const val BASE = "https://github.com/ArdaYurt/mii_os/releases/latest/download/"
    private const val PREFS = "updater"
    private const val CHECK_EVERY_MS = 30 * 60 * 1000L

    /** A published build: [code] orders builds, [name] is the version people see, [notes] its highlights. */
    data class Release(val code: Int, val name: String, val notes: List<String>)

    /** A newer build than the one running, or null. Checks at most every half hour. */
    suspend fun check(context: Context, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val json = if (!force && now - prefs.getLong("checkedAt", 0L) < CHECK_EVERY_MS) {
            prefs.getString("latestJson", null)
        } else {
            runCatching { fetch(URL(BASE + "version.json")).bufferedReader().use { it.readText() } }.getOrNull()
                ?.also { prefs.edit().putLong("checkedAt", now).putString("latestJson", it).apply() }
        } ?: return@withContext null
        val release = runCatching {
            val obj = JSONObject(json)
            val notes = obj.optJSONArray("notes")
            Release(
                code = obj.getInt("versionCode"),
                name = obj.optString("versionName").ifEmpty { "new version" },
                notes = List(notes?.length() ?: 0) { notes!!.getString(it) },
            )
        }.getOrNull() ?: return@withContext null
        release.takeIf { it.code > current(context) }
    }

    /** The version running now, as people see it. */
    fun currentName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /**
     * Android only installs apps from sources the owner has allowed. Mii_OS asks once; this opens
     * the screen where that's switched on, and returns false that one time. After that it goes
     * ahead: some TV boxes report the switch as off even when it's on, and Android's installer
     * asks again itself if it really is off.
     */
    fun canInstall(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("askedForSources", false)) return true
        prefs.edit().putBoolean("askedForSources", true).apply()
        val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(settings)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Toast.makeText(context, "Turn on Mii_OS, then press Back and choose Update again", Toast.LENGTH_LONG).show()
        return false
    }

    /** Downloads the latest APK and hands it to Android's installer, which asks the owner to confirm. */
    suspend fun install(context: Context, onProgress: (Float) -> Unit) {
        val apk = File(context.cacheDir, "mii-os-update.apk")
        withContext(Dispatchers.IO) {
            val connection = open(URL(BASE + "mii-os.apk"))
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("mii-os.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
            apk.delete()
        }
    }

    private fun current(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

    private fun fetch(url: URL) = open(url).inputStream

    private fun open(url: URL): HttpURLConnection {
        // GitHub answers release links with a redirect to its download host; follow it by hand
        // so a redirect between hosts is never dropped.
        var target = url
        repeat(5) {
            val c = target.openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            val code = c.responseCode
            if (code in 300..399) {
                target = URL(target, c.getHeaderField("Location"))
                c.disconnect()
            } else {
                if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("HTTP $code for $target")
                return c
            }
        }
        throw java.io.IOException("Too many redirects for $url")
    }
}

/** Android's installer reports back here: it needs the owner's OK, or it finished. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                // A different signing key is the usual cause: the old build has to be uninstalled once.
                val hint = if ("signature" in message.lowercase() || "conflict" in message.lowercase()) {
                    " This build was signed differently: uninstall Mii_OS once and install it again."
                } else ""
                Toast.makeText(context, "Update didn't install. $message$hint", Toast.LENGTH_LONG).show()
            }
        }
    }
}
