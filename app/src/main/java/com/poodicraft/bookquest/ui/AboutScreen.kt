package com.poodicraft.bookquest.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poodicraft.bookquest.BuildConfig
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.CrashLog
import com.poodicraft.bookquest.ui.components.SectionHeader

/** Where to write when something is wrong. */
private const val SUPPORT_EMAIL = "poodicraft@gmail.com"

/**
 * Everything a shipped app is expected to be able to answer about itself: what
 * version this is, what it does with your data, what it is built out of, and
 * how to reach a person about it.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }
    var noEmailApp by remember { mutableStateOf(false) }

    val crash = remember { CrashLog.read(context) }

    if (showPrivacy) {
        PrivacyScreen(onBack = { showPrivacy = false })
        return
    }
    if (showLicences) {
        LicencesScreen(onBack = { showLicences = false })
        return
    }
    if (showCrash && crash != null) {
        CrashScreen(report = crash, onBack = { showCrash = false })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "📚", fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.version_full,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.made_with),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    AboutRow(
                        emoji = "🔐",
                        label = stringResource(R.string.privacy_policy),
                        onClick = { showPrivacy = true }
                    )
                    AboutRow(
                        emoji = "⚖️",
                        label = stringResource(R.string.open_source),
                        onClick = { showLicences = true }
                    )
                    if (crash != null) {
                        AboutRow(
                            emoji = "🐞",
                            label = stringResource(R.string.last_crash),
                            onClick = { showCrash = true }
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "✉️ " + stringResource(R.string.contact_us)) }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.contact_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { noEmailApp = !sendSupportEmail(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.send_email))
                    }
                    if (noEmailApp) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_email_app) + "\n" + SUPPORT_EMAIL,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun AboutRow(emoji: String, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "$emoji  $label", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(text = "›", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Opens a mail app pre-addressed, with the version and device already filled in
 * so a report arrives with the details that make it actionable.
 */
private fun sendSupportEmail(context: Context): Boolean {
    val body = buildString {
        appendLine()
        appendLine("---")
        appendLine("BookQuest ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${android.os.Build.VERSION.RELEASE}")
        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "BookQuest ${BuildConfig.VERSION_NAME}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    val sections = listOf(
        R.string.privacy_h1 to R.string.privacy_p1,
        R.string.privacy_h2 to R.string.privacy_p2,
        R.string.privacy_h3 to R.string.privacy_p3,
        R.string.privacy_h4 to R.string.privacy_p4,
        R.string.privacy_h5 to R.string.privacy_p5,
        R.string.privacy_h6 to R.string.privacy_p6,
        R.string.privacy_h7 to R.string.privacy_p7
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.privacy_policy),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.privacy_updated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(sections.size) { index ->
            val (heading, body) = sections[index]
            Column {
                Text(
                    text = stringResource(heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        item {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

private data class Licence(val name: String, val holder: String, val licence: String)

private val LICENCES = listOf(
    Licence("Jetpack Compose", "The Android Open Source Project", "Apache License 2.0"),
    Licence("AndroidX Core, AppCompat, Activity", "The Android Open Source Project", "Apache License 2.0"),
    Licence("AndroidX Lifecycle", "The Android Open Source Project", "Apache License 2.0"),
    Licence("AndroidX Navigation", "The Android Open Source Project", "Apache License 2.0"),
    Licence("AndroidX Credentials", "The Android Open Source Project", "Apache License 2.0"),
    Licence("Material Components and icons", "Google LLC", "Apache License 2.0"),
    Licence("Firebase Android SDK", "Google LLC", "Apache License 2.0"),
    Licence("Google Identity Services", "Google LLC", "Apache License 2.0"),
    Licence("Kotlin standard library", "JetBrains s.r.o.", "Apache License 2.0"),
    Licence("kotlinx.coroutines", "JetBrains s.r.o.", "Apache License 2.0")
)

@Composable
private fun LicencesScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.open_source),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        items(LICENCES.size) { index ->
            val entry = LICENCES[index]
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.holder,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.licence,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun CrashScreen(report: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.last_crash),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            Text(
                text = stringResource(R.string.crash_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = report.take(4000),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    copyToClipboard(context, report)
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (copied) stringResource(R.string.report_copied)
                    else stringResource(R.string.copy_report)
                )
            }
        }
        item {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("BookQuest", text))
    } catch (e: Exception) {
        // Nothing useful to say if the clipboard itself is unavailable.
    }
}
