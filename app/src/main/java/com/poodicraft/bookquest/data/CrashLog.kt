package com.poodicraft.bookquest.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches a crash on its way out and writes it down.
 *
 * The app has no crash reporting service behind it, so without this a crash is
 * simply a window closing and a student with nothing to tell anyone. The report
 * is kept on the device, shown once on the next launch, and can be copied out
 * and sent on. Nothing is uploaded anywhere.
 *
 * The previous handler is always called afterwards, so the process still dies
 * the way Android expects rather than being left in a half-dead state.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(appContext, thread, error)
            } catch (e: Throwable) {
                // A crash while recording a crash helps nobody; fall through
                // to the handler that was already there.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun read(context: Context): String? = try {
        val target = file(context)
        if (target.exists()) target.readText().takeIf { it.isNotBlank() } else null
    } catch (e: Exception) {
        null
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (e: Exception) {
            // Nothing worth reporting about a report that will not delete.
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter()
        error.printStackTrace(PrintWriter(stack))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCodeCompat()})"
        } catch (e: Exception) {
            "unknown"
        }
        val text = buildString {
            appendLine("BookQuest $version")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine(stamp)
            appendLine()
            append(stack.toString())
        }
        file(context).writeText(text)
    }
}

@Suppress("DEPRECATION")
private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
