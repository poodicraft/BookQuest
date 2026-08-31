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
        CrashLog.install(this)

        // Hebrew is the default language of the app, whatever the device is set to.
        val prefs = Prefs(this)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(prefs.language)
        )

        // A convenience, not a reason for the app to fail to open.
        try {
            Reminders.ensureChannel(this)
        } catch (e: Throwable) {
            // No channel means no reminder, which is survivable.
        }
    }
}
