package com.poodicraft.bookquest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.AccountState
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.data.UserRole
import kotlinx.coroutines.launch

/**
 * Asked once, right after signing in: are you a teacher or a student, and who
 * are you. It belongs to the account rather than to the class tab, because the
 * answer decides what the whole app looks like.
 *
 * [onSkip] is only passed when the screen is reached from settings to edit the
 * details later; during onboarding there is nothing to go back to.
 */
@Composable
fun RoleScreen(
    onDone: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val classroom = remember { Classroom.get(context) }
    val cloud = remember { CloudSync.get(context) }
    val scope = rememberCoroutineScope()

    val account by cloud.account.collectAsStateWithLifecycle()
    val profile by classroom.profile.collectAsStateWithLifecycle()

    val signedInName = (account as? AccountState.SignedIn)?.name.orEmpty()

    var role by remember {
        mutableStateOf(
            if (profile.role == UserRole.UNKNOWN) UserRole.STUDENT else profile.role
        )
    }
    var name by remember {
        mutableStateOf(profile.displayName.ifBlank { signedInName })
    }
    var school by remember { mutableStateOf(profile.school) }
    var subject by remember { mutableStateOf(profile.subject) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(text = "👋", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.role_question),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        item {
            RoleOption(
                emoji = "🎒",
                title = stringResource(R.string.role_student),
                body = stringResource(R.string.role_student_desc),
                selected = role == UserRole.STUDENT,
                onClick = { role = UserRole.STUDENT }
            )
        }
        item {
            RoleOption(
                emoji = "🍎",
                title = stringResource(R.string.role_teacher),
                body = stringResource(R.string.role_teacher_desc),
                selected = role == UserRole.TEACHER,
                onClick = { role = UserRole.TEACHER }
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.your_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = school,
                onValueChange = { school = it },
                label = { Text(stringResource(R.string.school_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (role == UserRole.TEACHER) {
            item {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.subject_taught)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (failed) {
            item {
                Text(
                    text = stringResource(R.string.something_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            Button(
                onClick = {
                    if (name.isBlank() || busy) return@Button
                    busy = true
                    failed = false
                    scope.launch {
                        val result = classroom.saveProfile(role, name, school, subject)
                        if (result.isSuccess) {
                            classroom.ensureLoaded(force = true)
                            busy = false
                            onDone()
                        } else {
                            busy = false
                            failed = true
                        }
                    }
                },
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.save_profile))
            }
        }
        if (onSkip != null) {
            item {
                TextButton(
                    onClick = onSkip,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun RoleOption(
    emoji: String,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Text(text = "✓", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
