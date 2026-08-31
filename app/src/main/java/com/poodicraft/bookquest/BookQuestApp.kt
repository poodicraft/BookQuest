package com.poodicraft.bookquest

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.poodicraft.bookquest.data.CrashLog
import com.poodicraft.bookquest.data.Prefs
import com.poodicraft.bookquest.data.Reminders

class BookQuestApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // First thing, so a crash during the rest of start up is still recorded.
        try {
            CrashLog.install(this)
        } catch (e: Throwable) {
            // Losing the crash recorder is not worth losing the app over.
        }

        // Hebrew is the default language of the app, whatever the device is set to.
        try {
            val prefs = Prefs(this)
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(prefs.language)
            )
        } catch (e: Throwable) {
            // Fall back to the device language rather than refusing to start.
        }

        // Every one of these is a convenience. None of them is a reason for the
        // app to fail to open, so none of them is allowed to throw out of here.
        try {
            Reminders.ensureChannel(this)
        } catch (e: Throwable) {
            // No channel means no reminder, which is survivable.
        }
    }
}
