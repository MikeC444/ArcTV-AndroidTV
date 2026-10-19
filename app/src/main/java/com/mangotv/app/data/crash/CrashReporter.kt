package com.mangotv.app.data.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.network.AccountApiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One crash, as POST /crash-reports takes it (see server/src/schemas/crashReports.ts). */
@Serializable
data class CrashReport(
    val platform: String,
    val appVersion: String,
    val deviceModel: String?,
    val osVersion: String?,
    val kind: String,
    val exception: String?,
    val stackTrace: String,
    val happenedAt: String
)

/**
 * Crash reports for the developer panel. When the app closes unexpectedly, what went wrong is saved to a small file; the next time the app
 * opens it is sent to the account server (POST /crash-reports, with the account when signed in) and the file is deleted.
 *
 * Two sources: an uncaught Kotlin/Java error, caught by [install] as it happens; and, on Android 11 and newer, the system's own record of why
 * the app last ended ([ApplicationExitInfo]): a native crash (VLC, the torrent engine), a freeze it ended (ANR), or being closed for low
 * memory while on screen -- none of which an error handler ever sees. Nothing is shown to the person.
 */
object CrashReporter {
    private const val DIR = "crash-reports"
    private const val MAX_FILES = 5
    private const val MAX_TRACE = 16_000
    private const val PREFS = "crash_reporter"
    private const val LAST_EXIT_KEY = "last_exit_timestamp"
    // Same name the app signs in with (AuthRepository), so reports sit next to the device's version in the developer panel.
    private const val PLATFORM = "fire_tv"
    private val json = Json { ignoreUnknownKeys = true }

    /** Saves any uncaught error before the app closes, then lets Android close it as usual. Call first thing in Application.onCreate. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save(appContext, fromThrowable(thread, error))
            } catch (_: Throwable) {
                // never stand in the way of the crash itself
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /**
     * Sends what was saved (after first adding the system's record of how the app last ended, on Android 11+). Files go once the server has
     * answered; when it can't be reached they wait for the next start. [accessToken] gives the signed-in account's token, or null.
     */
    suspend fun sendPending(context: Context, baseUrl: String, accessToken: suspend () -> String?) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { collectExitReasons(context) }
        val files = File(context.filesDir, DIR).listFiles()?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return@withContext
        val token = runCatching { accessToken() }.getOrNull()
        for (file in files) {
            val body = runCatching { file.readText() }.getOrNull()
            if (body == null) {
                file.delete()
                continue
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/crash-reports")
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            val code = runCatching { AccountApiHttpClient.client.newCall(request).execute().use { it.code } }.getOrNull()
                ?: return@withContext // offline or the server is down: try again next start
            // Answered (kept, or refused as malformed): done with it. Busy or failing: keep it for next time.
            if (code in 200..499 && code != 429) file.delete() else return@withContext
        }
    }

    private fun fromThrowable(thread: Thread, error: Throwable): CrashReport {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return report(
            kind = "crash",
            exception = error.toString().take(500),
            stackTrace = "Thread: ${thread.name}\n$writer",
            happenedAtMillis = System.currentTimeMillis()
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collectExitReasons(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_EXIT_KEY, 0L)
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        var newest = last
        for (info in exits) {
            if (info.timestamp <= last) continue
            newest = maxOf(newest, info.timestamp)
            val onScreen = info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            val kind = when (info.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
                ApplicationExitInfo.REASON_ANR -> "anr"
                // Closed for memory in the background is ordinary Android housekeeping; only while on screen is it a crash to the person.
                ApplicationExitInfo.REASON_LOW_MEMORY -> if (onScreen) "low_memory" else null
                ApplicationExitInfo.REASON_SIGNALED -> if (onScreen) "other_exit" else null
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "other_exit"
                // REASON_CRASH is already caught by install(); the rest are exits the person or the system chose (closed, updated, idle)
                else -> null
            } ?: continue
            val details = buildString {
                append("Exit reason: ").append(reasonName(info.reason)).append('\n')
                append("On screen: ").append(onScreen).append(" (importance ").append(info.importance).append(")\n")
                append("Memory: PSS ").append(info.pss).append(" kB, RSS ").append(info.rss).append(" kB\n")
                append("Status: ").append(info.status).append('\n')
                append("Reported on the next start (the version that closed may be older than the one reporting).\n")
                readTrace(info)?.let { append('\n').append(it) }
            }
            save(context, report(kind = kind, exception = info.description?.take(500), stackTrace = details, happenedAtMillis = info.timestamp))
        }
        prefs.edit().putLong(LAST_EXIT_KEY, newest).apply()
    }

    /** The system's trace for an ANR or a native crash, when it is readable text (a newer native crash tombstone is binary and left out). */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readTrace(info: ApplicationExitInfo): String? = runCatching {
        info.traceInputStream?.use { stream ->
            val bytes = ByteArray(64 * 1024)
            var read = 0
            while (read < bytes.size) {
                val n = stream.read(bytes, read, bytes.size - read)
                if (n < 0) break
                read += n
            }
            val text = String(bytes, 0, read, Charsets.UTF_8)
            val printable = text.count { it == '\n' || it == '\t' || it.code in 32..126 }
            if (text.isEmpty() || printable < text.length * 0.9) "(the system's trace is binary and was left out)" else text
        }
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_SIGNALED -> "signalled"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialisation failure"
        else -> "reason $reason"
    }

    private fun report(kind: String, exception: String?, stackTrace: String, happenedAtMillis: Long) = CrashReport(
        platform = PLATFORM,
        appVersion = BuildConfig.VERSION_NAME,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120),
        osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        kind = kind,
        exception = exception,
        stackTrace = stackTrace.take(MAX_TRACE),
        happenedAt = isoUtc(happenedAtMillis)
    )

    private fun save(context: Context, report: CrashReport) {
        val dir = File(context.filesDir, DIR)
        dir.mkdirs()
        val existing = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        // Only the latest few: a crash on every start must not fill the device.
        existing.take(maxOf(0, existing.size - (MAX_FILES - 1))).forEach { it.delete() }
        File(dir, "${System.currentTimeMillis()}-${report.kind}.json").writeText(json.encodeToString(CrashReport.serializer(), report))
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
}
