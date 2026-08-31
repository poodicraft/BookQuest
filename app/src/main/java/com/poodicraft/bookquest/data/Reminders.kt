package com.poodicraft.bookquest.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.poodicraft.bookquest.MainActivity
import com.poodicraft.bookquest.R
import java.util.Calendar

/**
 * The daily nudge to read.
 *
 * It is deliberately an inexact repeating alarm. A reminder that lands within
 * a few minutes of the chosen time is exactly as useful as one that lands on
 * the second, and asking for an exact alarm costs a restricted permission the
 * user would have to grant by hand in system settings.
 */
object Reminders {

    const val CHANNEL_ID = "daily_reading"
    private const val REQUEST_CODE = 4021
    private const val NOTIFICATION_ID = 4022

    /** Creates the channel. Safe to call repeatedly; Android ignores repeats. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_hint)
        }
        manager.createNotificationChannel(channel)
    }

    /** Puts the alarm in place for the time held in [Prefs], or clears it. */
    fun apply(context: Context) {
        val prefs = Prefs(context)
        if (prefs.reminderOn) schedule(context, prefs.reminderHour, prefs.reminderMinute)
        else cancel(context)
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        ensureChannel(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextOccurrence(hour, minute)
        try {
            alarms.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                at,
                AlarmManager.INTERVAL_DAY,
                pendingIntent(context)
            )
        } catch (e: Exception) {
            // Some devices refuse alarms to apps they have put to sleep. The
            // preference stays on, and the alarm is set again on the next launch.
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            alarms.cancel(pendingIntent(context))
        } catch (e: Exception) {
            // Nothing was scheduled, which is the state we wanted anyway.
        }
    }

    /** The next time today or tomorrow that the clock reads [hour]:[minute]. */
    fun nextOccurrence(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /**
     * Posts the reminder, unless there has already been reading today — nagging
     * someone who has just put the book down is how an app gets muted.
     */
    fun notifyNow(context: Context) {
        val profile = LibraryRepository.get(context).profile.value
        if (profile.minutesToday > 0) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val content = PendingIntent.getActivity(context, 0, open, flags)

        val streak = profile.streak
        val body = if (streak > 0) {
            context.getString(R.string.reminder_body_streak, streak)
        } else {
            context.getString(R.string.reminder_body)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission was refused or withdrawn. Nothing to do.
        }
    }
}

/** Wakes on the daily alarm and posts the reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Reminders.notifyNow(context)
    }
}

/**
 * Alarms do not survive a reboot, so they are put back as the device comes up.
 * Without this the reminder silently stops the first time the phone restarts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Reminders.apply(context)
        }
    }
}
