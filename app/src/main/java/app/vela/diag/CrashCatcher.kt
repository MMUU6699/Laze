package app.vela.diag

import android.content.Context
import android.content.Intent
import android.os.Build
import app.vela.BuildConfig
import app.vela.core.diag.DiagEvent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Catches an otherwise-fatal uncaught exception and **persists** a crash report —
 * stack trace, app/device versions, and whatever diagnostic breadcrumbs were in
 * memory — to a file under `filesDir/diag/crash/`, then chains to the system's
 * default handler so the normal crash flow is unchanged.
 *
 * Why this exists: when nav crashed on a phone that wasn't tethered, there was no
 * way to get the stack trace. This keeps it on disk so the user can **export the
 * crash report on the next launch** (Settings → Diagnostics) and hand it to a dev.
 *
 * The stack trace + device info are benign (no personal data), so a report is
 * written even if the opt-in diagnostics log is off — the breadcrumb section is
 * simply empty in that case (breadcrumbs are only recorded when the user opted in).
 * The report never leaves the phone unless the user exports + shares it.
 */
object CrashCatcher {

    fun install(context: Context, breadcrumbs: () -> List<DiagEvent>) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            runCatching { writeReport(app, ex, breadcrumbs()) }
            prev?.uncaughtException(thread, ex)
        }
    }

    private fun dir(context: Context) = File(context.filesDir, "diag/crash").apply { mkdirs() }

    private fun writeReport(context: Context, ex: Throwable, crumbs: List<DiagEvent>) {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("Vela crash report\n")
            append("when: ").append(System.currentTimeMillis()).append('\n')
            append("version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("android: API ").append(Build.VERSION.SDK_INT)
                .append(" — ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")
            append("=== settings ===\n").append(SettingsDump.line(context)).append("\n\n")
            append("=== stack trace ===\n").append(sw.toString()).append('\n')
            append("=== breadcrumbs (").append(crumbs.size).append(") ===\n")
            crumbs.forEach { e ->
                append(e.epochMs).append(" [").append(e.kind).append("] ").append(e.summary)
                e.detail?.let { append(" :: ").append(it) }
                append('\n')
            }
        }
        File(dir(context), "crash-${System.currentTimeMillis()}.txt").writeText(text)
        prune(context)
    }

    /** Keep only the few most recent reports so this can't grow unbounded. */
    private fun prune(context: Context, keep: Int = 5) {
        val files = pending(context)
        if (files.size > keep) files.dropLast(keep).forEach { runCatching { it.delete() } }
    }

    /** Crash reports on disk, oldest first. */
    fun pending(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.startsWith("crash-") }?.sortedBy { it.name } ?: emptyList()

    fun clear(context: Context) {
        pending(context).forEach { runCatching { it.delete() } }
    }

    /** Share the most recent crash report via the system sheet, or null if none. */
    fun shareIntent(context: Context): Intent? {
        val newest = pending(context).lastOrNull() ?: return null
        return shareFileIntent(
            context, newest,
            mime = "text/plain",
            subject = "Vela crash report",
            text = "Attached: a Vela crash report (stack trace + diagnostics).",
            title = "Share crash report",
        )
    }

    /**
     * Checks ActivityManager.getHistoricalProcessExitReasons (API 30+) on boot.
     * Records any abnormal termination (OOM / native crash / ANR / signal kill) to disk
     * under `filesDir/diag/crash/crash-exit-<timestamp>.txt` and to DiagLog.
     */
    fun checkHistoricalExitReasons(context: Context, diagLog: app.vela.core.diag.DiagLog? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return
        val exitList = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 5) }.getOrNull() ?: return
        val lastAbnormal = exitList.firstOrNull { exit ->
            exit.reason != android.app.ApplicationExitInfo.REASON_USER_REQUESTED &&
            exit.reason != android.app.ApplicationExitInfo.REASON_USER_STOPPED &&
            exit.reason != android.app.ApplicationExitInfo.REASON_EXIT_SELF
        } ?: return

        val prefs = context.getSharedPreferences("vela_exit_diag", Context.MODE_PRIVATE)
        val lastRecordedTimestamp = prefs.getLong("last_exit_ts", 0L)
        if (lastAbnormal.timestamp <= lastRecordedTimestamp) return
        prefs.edit().putLong("last_exit_ts", lastAbnormal.timestamp).apply()

        val reasonName = when (lastAbnormal.reason) {
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (OOM killer)"
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
            android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
            android.app.ApplicationExitInfo.REASON_CRASH -> "UNCAUGHT_EXCEPTION_CRASH"
            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (status=${lastAbnormal.status})"
            android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            else -> "OTHER (${lastAbnormal.reason})"
        }

        val traceSnippet = runCatching {
            lastAbnormal.traceInputStream?.use { stream ->
                val buf = ByteArray(8192)
                val read = stream.read(buf)
                if (read > 0) String(buf, 0, read) else null
            }
        }.getOrNull()

        val reportText = buildString {
            append("Vela historical process exit report\n")
            append("when: ").append(lastAbnormal.timestamp).append('\n')
            append("reason: ").append(reasonName).append(" (code: ").append(lastAbnormal.reason).append(")\n")
            append("description: ").append(lastAbnormal.description ?: "none").append('\n')
            append("importance: ").append(lastAbnormal.importance).append('\n')
            append("pss: ").append(lastAbnormal.pss / 1024).append(" MB\n")
            append("rss: ").append(lastAbnormal.rss / 1024).append(" MB\n")
            append("version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("android: API ").append(Build.VERSION.SDK_INT)
                .append(" — ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")
            append("=== settings ===\n").append(SettingsDump.line(context)).append("\n\n")
            if (!traceSnippet.isNullOrBlank()) {
                append("=== trace snippet ===\n").append(traceSnippet).append("\n\n")
            }
        }

        File(dir(context), "crash-exit-${lastAbnormal.timestamp}.txt").writeText(reportText)
        prune(context)
        android.util.Log.w("VelaApp", "Previous abnormal process exit detected: $reasonName (pss=${lastAbnormal.pss / 1024}MB)")
        diagLog?.record("process_exit", reasonName, "pss=${lastAbnormal.pss / 1024}MB desc=${lastAbnormal.description}")
        TripNote.add("prev-exit: $reasonName pss=${lastAbnormal.pss / 1024}MB")
    }
}
