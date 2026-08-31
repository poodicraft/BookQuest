package com.poodicraft.bookquest.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poodicraft.bookquest.BuildConfig
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.AccountState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.Prefs
import com.poodicraft.bookquest.data.Reminders
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.data.Profile
import com.poodicraft.bookquest.data.SyncState
import com.poodicraft.bookquest.data.UserRole
import com.poodicraft.bookquest.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LanguageOption(val tag: String, val labelRes: Int, val flag: String)

private val LANGUAGES = listOf(
    LanguageOption("he", R.string.language_hebrew, "🇮🇱"),
    LanguageOption("en", R.string.language_english, "🇬🇧"),
    LanguageOption("ar", R.string.language_arabic, "🇸🇦")
)

@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    profile: Profile,
    repository: LibraryRepository
) {
    val context = LocalContext.current
    val classroom = remember { Classroom.get(context) }
    val schoolProfile by classroom.profile.collectAsStateWithLifecycle()
    // A daily reading goal belongs to the reader, not to whoever set the work.
    val isTeacher = schoolProfile.role == UserRole.TEACHER

    val prefs = remember { Prefs(context) }

    var goal by remember { mutableFloatStateOf(profile.dailyGoal.toFloat()) }
    var showSignIn by remember { mutableStateOf(false) }
    var showRole by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    if (showSignIn) {
        AuthScreen(
            onSkip = { showSignIn = false },
            onSignedIn = { showSignIn = false }
        )
        return
    }

    if (showRole) {
        ProfileScreen(onBack = { showRole = false })
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item { SectionHeader(title = "☁️ " + stringResource(R.string.account)) }

        item {
            AccountCard(
                onSignIn = { showSignIn = true },
                onEditDetails = { showRole = true }
            )
        }

        item { SectionHeader(title = "🌍 " + stringResource(R.string.language)) }

        items(LANGUAGES.size) { position ->
            val option = LANGUAGES[position]
            val selected = language == option.tag
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!selected) onLanguageChange(option.tag)
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = option.flag, fontSize = 26.sp)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Text(text = "✓", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { SectionHeader(title = "🎨 " + stringResource(R.string.appearance)) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "system" to R.string.theme_system,
                    "light" to R.string.theme_light,
                    "dark" to R.string.theme_dark
                ).forEach { (key, labelRes) ->
                    FilterChip(
                        selected = themeMode == key,
                        onClick = { onThemeModeChange(key) },
                        label = { Text(stringResource(labelRes)) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }

        if (!isTeacher) {
            item { SectionHeader(title = "🎯 " + stringResource(R.string.daily_goal)) }

            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.daily_goal_minutes, goal.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = goal,
                            onValueChange = { goal = it },
                            onValueChangeFinished = { repository.setDailyGoal(goal.toInt()) },
                            valueRange = 5f..90f,
                            steps = 16
                        )
                    }
                }
            }
        }

        if (!isTeacher) {
            item { SectionHeader(title = "⏰ " + stringResource(R.string.reminder_section)) }
            item { ReminderCard(prefs = prefs) }
        }

        item { SectionHeader(title = "💾 " + stringResource(R.string.your_data)) }
        item { DataCard(repository = repository) }

        item { SectionHeader(title = "ℹ️ " + stringResource(R.string.about)) }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAbout = true },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ℹ️  " + stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "›", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.about_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            R.string.version_label,
                            BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.supported_formats),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

/**
 * The daily reading nudge.
 *
 * The notification permission is asked for at the moment the switch is turned
 * on and never at launch: a permission dialog before anyone has seen what the
 * app does is the fastest way to have it refused for good.
 */
@Composable
private fun ReminderCard(prefs: Prefs) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(prefs.reminderOn) }
    var hour by remember { mutableIntStateOf(prefs.reminderHour) }
    var minute by remember { mutableIntStateOf(prefs.reminderMinute) }
    var refused by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refused = !granted
        if (granted) {
            on = true
            prefs.reminderOn = true
            Reminders.schedule(context, hour, minute)
        }
    }

    fun switchOn() {
        refused = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        on = true
        prefs.reminderOn = true
        Reminders.schedule(context, hour, minute)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reminder_switch),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.reminder_at, clockText(hour, minute)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { wanted ->
                        if (wanted) {
                            switchOn()
                        } else {
                            on = false
                            prefs.reminderOn = false
                            Reminders.cancel(context)
                        }
                    }
                )
            }

            if (refused) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reminder_blocked),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (on) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.hour_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { hour = it.toInt() },
                    onValueChangeFinished = {
                        prefs.reminderHour = hour
                        Reminders.schedule(context, hour, minute)
                    },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Text(
                    text = stringResource(R.string.minute_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = minute.toFloat(),
                    onValueChange = { minute = (it / 5).toInt() * 5 },
                    onValueChangeFinished = {
                        prefs.reminderMinute = minute
                        Reminders.schedule(context, hour, minute)
                    },
                    valueRange = 0f..55f,
                    steps = 10
                )
            }
        }
    }
}

/** 24 hour clock, zero padded, so 9:05 does not read as 9:5. */
private fun clockText(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

/**
 * Export and import.
 *
 * The cloud backup needs an account and a connection; this needs neither. It
 * writes the same snapshot the backup uses to a file the reader keeps, which is
 * the difference between progress being safe and progress being someone else's
 * responsibility.
 */
@Composable
private fun DataCard(repository: LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Int?>(null) }

    val exporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val written = writeSnapshot(context, uri, repository.exportSnapshot())
                message = if (written) R.string.export_done else R.string.something_failed
            }
        }
    }

    val importer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = readSnapshot(context, uri)
                message = when {
                    text == null -> R.string.something_failed
                    !text.trimStart().startsWith("{") -> R.string.import_bad_file
                    else -> {
                        repository.mergeSnapshot(text)
                        R.string.import_done
                    }
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.export_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        message = null
                        exporter.launch(defaultBackupName())
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export_data))
                }
                OutlinedButton(
                    onClick = {
                        message = null
                        importer.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.import_data))
                }
            }
            val shown = message
            if (shown != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(shown),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (shown == R.string.export_done || shown == R.string.import_done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

private fun defaultBackupName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "bookquest-$stamp.json"
}

private suspend fun writeSnapshot(context: Context, uri: Uri, json: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            false
        }
    }

private suspend fun readSnapshot(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

@Composable
private fun AccountCard(onSignIn: () -> Unit, onEditDetails: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloud = remember { CloudSync.get(context) }
    val classroom = remember { Classroom.get(context) }
    val school by classroom.profile.collectAsStateWithLifecycle()
    val account by cloud.account.collectAsStateWithLifecycle()
    val sync by cloud.sync.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    cloud.signOut()
                }) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            busy = busy,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                busy = true
                deleteFailed = false
                scope.launch {
                    // The classes go first, while the account still has the
                    // permission that lets it remove them.
                    classroom.leaveEverything()
                    val result = cloud.deleteAccount()
                    busy = false
                    confirmDelete = false
                    if (result.isFailure) deleteFailed = true
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            when (val state = account) {
                is AccountState.NotConfigured -> {
                    Text(
                        text = "🔒 " + stringResource(R.string.cloud_not_configured),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.cloud_not_configured_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AccountState.SignedOut -> {
                    Text(
                        text = stringResource(R.string.account_signed_out),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.account_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(stringResource(R.string.sign_in_title))
                    }
                }

                is AccountState.SignedIn -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(
                            photo = school.photo,
                            name = school.displayName.ifBlank { state.name },
                            size = 52.dp
                        )
                        Spacer(Modifier.padding(horizontal = 7.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = school.displayName
                                    .ifBlank { state.name }
                                    .ifBlank { state.email },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.email,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val providerLabel = when (state.provider) {
                                "google.com" -> "Google"
                                "password" -> stringResource(R.string.email_label)
                                else -> ""
                            }
                            if (providerLabel.isNotEmpty()) {
                                Text(
                                    text = providerLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when (val current = sync) {
                            is SyncState.Working -> stringResource(R.string.syncing)
                            is SyncState.Done -> stringResource(
                                R.string.sync_done,
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(current.at))
                            )
                            is SyncState.Failed -> stringResource(R.string.sync_failed, current.reason)
                            else -> stringResource(R.string.account_hint)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sync is SyncState.Failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (school.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = school.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onEditDetails) {
                        Text(stringResource(R.string.edit_profile))
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { confirmSignOut = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.sign_out))
                        }
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    cloud.syncNow()
                                    busy = false
                                }
                            },
                            enabled = !busy && sync !is SyncState.Working,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.sync_now))
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            text = stringResource(R.string.delete_account),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (deleteFailed) {
                        Text(
                            text = stringResource(R.string.delete_account_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deleting an account is not undoable and not recoverable, so it asks for the
 * word to be typed rather than for one more tap. A tap is something a phone in
 * a pocket can do; typing is not.
 */
@Composable
private fun DeleteAccountDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val word = stringResource(R.string.delete_word)
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(word, ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Text(text = "\u26A0\uFE0F", fontSize = 28.sp) },
        title = { Text(stringResource(R.string.delete_account)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_account_hint))
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.delete_account_confirm, word),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches && !busy) {
                Text(
                    text = stringResource(R.string.delete_account),
                    color = if (matches) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
