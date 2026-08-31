package com.poodicraft.bookquest.data

import android.content.Context

/** Small wrapper around SharedPreferences for user choices. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("bookquest_prefs", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = sp.edit().putString(KEY_LANGUAGE, value).apply()

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = sp.getString(KEY_THEME, "system") ?: "system"
        set(value) = sp.edit().putString(KEY_THEME, value).apply()

    var readerFontSize: Float
        get() = sp.getFloat(KEY_FONT, 18f)
        set(value) = sp.edit().putFloat(KEY_FONT, value).apply()

    /** "light", "sepia" or "dark". */
    var readerTheme: String
        get() = sp.getString(KEY_READER_THEME, "light") ?: "light"
        set(value) = sp.edit().putString(KEY_READER_THEME, value).apply()

    var starterBooksAdded: Boolean
        get() = sp.getBoolean(KEY_STARTER, false)
        set(value) = sp.edit().putBoolean(KEY_STARTER, value).apply()

    /** Whether the daily reading reminder is switched on. */
    var reminderOn: Boolean
        get() = sp.getBoolean(KEY_REMINDER_ON, false)
        set(value) = sp.edit().putBoolean(KEY_REMINDER_ON, value).apply()

    /** Hour of day, 0..23, that the reminder fires. */
    var reminderHour: Int
        get() = sp.getInt(KEY_REMINDER_HOUR, 18)
        set(value) = sp.edit().putInt(KEY_REMINDER_HOUR, value.coerceIn(0, 23)).apply()

    var reminderMinute: Int
        get() = sp.getInt(KEY_REMINDER_MINUTE, 0)
        set(value) = sp.edit().putInt(KEY_REMINDER_MINUTE, value.coerceIn(0, 59)).apply()

    /**
     * Set as the app starts and cleared once the first screen has actually been
     * drawn. Finding it still set on the next launch means the app died on the
     * way up, which is the one failure a user cannot get out of or report.
     */
    var launchInProgress: Boolean
        get() = sp.getBoolean(KEY_LAUNCHING, false)
        set(value) = sp.edit().putBoolean(KEY_LAUNCHING, value).commit().let { }

    /** True once the welcome tour has been seen, so it only runs on a first launch. */
    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDED, value).apply()

    companion object {
        const val DEFAULT_LANGUAGE = "he"
        val LANGUAGES = listOf("he", "en", "ar")

        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FONT = "reader_font"
        private const val KEY_READER_THEME = "reader_theme"
        private const val KEY_STARTER = "starter_books_added"
        private const val KEY_REMINDER_ON = "reminder_on"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_LAUNCHING = "launch_in_progress"
    }
}
