package com.poodicraft.bookquest

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.data.CrashLog
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.data.Prefs
import com.poodicraft.bookquest.data.Reminders
import com.poodicraft.bookquest.data.Subject
import com.poodicraft.bookquest.ui.BookQuestRoot
import com.poodicraft.bookquest.ui.SafeModeScreen
import com.poodicraft.bookquest.ui.theme.BookQuestTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Drop the launch background now that the app is up. The activity has
        // been running under an AppCompat theme all along; only the wallpaper
        // behind it changes here.
        setTheme(R.style.Theme_BookQuest)
        super.onCreate(savedInstanceState)

        val prefs = Prefs(this)

        // If this is still set, the previous launch died before anything was
        // drawn. Rather than looping into the same crash, open on the report so
        // there is something to read and something to send.
        val brokenLaunch = prefs.launchInProgress
        prefs.launchInProgress = true

        if (brokenLaunch) {
            val report = CrashLog.read(this)
            setContent {
                BookQuestTheme(themeMode = prefs.themeMode) {
                    SafeModeScreen(
                        report = report,
                        onContinue = {
                            prefs.launchInProgress = false
                            CrashLog.clear(this)
                            recreate()
                        }
                    )
                }
            }
            return
        }

        val repository = LibraryRepository.get(this)

        if (!prefs.starterBooksAdded) {
            prefs.starterBooksAdded = true
            repository.seedStarterBooks(
                listOf(
                    Triple("starter/welcome_he.txt", "ברוכים הבאים למסע הספרים", Subject.LANGUAGE),
                    Triple("starter/welcome_en.txt", "Welcome to BookQuest", Subject.LANGUAGE),
                    Triple("starter/welcome_ar.txt", "أهلا بك في رحلة الكتب", Subject.LANGUAGE)
                )
            )
        }

        // Alarms are lost on reboot and when the app is replaced, so the daily
        // reminder is put back every launch. It is a convenience, so it is never
        // allowed to stop the app opening.
        try {
            Reminders.apply(this)
        } catch (e: Throwable) {
            // No reminder this run. The library still works.
        }

        setContent {
            // Reaching a first composition is the definition of a launch that
            // worked, so this is where the crash-loop flag is cleared.
            LaunchedEffect(Unit) { prefs.launchInProgress = false }

            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var language by remember { mutableStateOf(prefs.language) }

            BookQuestTheme(themeMode = themeMode) {
                BookQuestRoot(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        prefs.themeMode = mode
                        themeMode = mode
                    },
                    language = language,
                    onLanguageChange = { tag ->
                        prefs.language = tag
                        language = tag
                        repository.noteLanguage(tag)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tag)
                        )
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Quietly push progress to the account, if one is signed in.
        val cloud = CloudSync.get(this)
        if (cloud.isConfigured) {
            lifecycleScope.launch { cloud.pushQuietly() }
        }
    }
}
