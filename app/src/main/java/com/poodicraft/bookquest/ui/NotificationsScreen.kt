package com.poodicraft.bookquest.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.Prefs
import com.poodicraft.bookquest.data.Reminders
import com.poodicraft.bookquest.ui.components.SectionHeader
import java.util.Locale

/** True when the system will actually deliver anything this app posts. */
internal fun notificationsAllowed(context: Context): Boolean = try {
    NotificationManagerCompat.from(context).areNotificationsEnabled()
} catch (e: Exception) {
    true
}

/** Opens this app's own page in the system notification settings. */
internal fun openNotificationSettings(context: Context) {
    val intents = buildList<Intent> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Try the next one; some devices are missing the specific screen.
        }
    }
}

/**
 * Everything the app is allowed to interrupt you for, in one place, for
 * everyone — a teacher reads too, and burying this under the student half of
 * settings meant half the people using the app could not find it at all.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var on by remember { mutableStateOf(prefs.reminderOn) }
    var hour by remember { mutableIntStateOf(prefs.reminderHour) }
    var minute by remember { mutableIntStateOf(prefs.reminderMinute) }

    val askPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        allowed = granted
        if (granted) {
            on = true
            prefs.reminderOn = true
            Reminders.schedule(context, hour, minute)
        }
    }

    fun turnOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !allowed) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        on = true
        prefs.reminderOn = true
        Reminders.schedule(context, hour, minute)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(text = "🔔", fontSize = 34.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.notifications),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.notifications_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // The app's own switch is meaningless while the system is blocking it,
        // so that gets said first, with the way to fix it.
        if (!allowed) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.notifications_blocked_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.notifications_blocked_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { openNotificationSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }
            }
        }

        item { SectionHeader(title = "⏰ " + stringResource(R.string.reminder_section)) }

        item {
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
                                text = stringResource(
                                    R.string.reminder_at,
                                    clockText(hour, minute)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = on && allowed,
                            onCheckedChange = { wanted ->
                                if (wanted) {
                                    turnOn()
                                } else {
                                    on = false
                                    prefs.reminderOn = false
                                    Reminders.cancel(context)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.reminder_skip_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (on && allowed) {
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

        if (allowed) {
            item {
                Text(
                    text = "✓ " + stringResource(R.string.notifications_allowed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                TextButton(onClick = { openNotificationSettings(context) }) {
                    Text(stringResource(R.string.open_settings))
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

/** 24 hour clock, zero padded, so 9:05 does not read as 9:5. */
internal fun clockText(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)
