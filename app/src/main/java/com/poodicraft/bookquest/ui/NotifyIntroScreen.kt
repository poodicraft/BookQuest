package com.poodicraft.bookquest.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.Prefs
import com.poodicraft.bookquest.data.Reminders
import com.poodicraft.bookquest.ui.components.AppBackground

/**
 * Asked once, on the first run after installing.
 *
 * This is a question in the app's own words before the system dialog, not
 * instead of it. Android only ever shows its permission dialog once; spending
 * that single chance on someone who has not yet seen what the app is for is how
 * an app ends up permanently unable to send anything. Saying no here costs
 * nothing and never raises the system prompt at all.
 */
@Composable
fun NotifyIntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    val askPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prefs.reminderOn = true
            Reminders.schedule(context, prefs.reminderHour, prefs.reminderMinute)
        }
        onDone()
    }

    fun accept() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsAllowed(context)
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        // Below Android 13 there is no permission to ask for; it is simply on.
        prefs.reminderOn = true
        Reminders.schedule(context, prefs.reminderHour, prefs.reminderMinute)
        onDone()
    }

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(horizontal = 26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🔔", fontSize = 56.sp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.notify_intro_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.notify_intro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(30.dp))
                Button(
                    onClick = { accept() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(stringResource(R.string.notify_intro_yes))
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notify_intro_no))
                }
            }
        }
    }
}
