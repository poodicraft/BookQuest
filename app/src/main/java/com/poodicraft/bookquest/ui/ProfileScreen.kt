package com.poodicraft.bookquest.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.AccountState
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.data.UserRole
import com.poodicraft.bookquest.ui.components.SectionHeader
import kotlinx.coroutines.launch

/**
 * Everything about the account in one place: the picture, the name, the bio,
 * which side of the classroom you are on, and the password.
 *
 * The role questionnaire at sign in ([RoleScreen]) asks the bare minimum to get
 * someone into the app. This is where they come back to fill the rest in.
 */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val classroom = remember { Classroom.get(context) }
    val cloud = remember { CloudSync.get(context) }
    val scope = rememberCoroutineScope()

    val account by cloud.account.collectAsStateWithLifecycle()
    val profile by classroom.profile.collectAsStateWithLifecycle()
    val signedInName = (account as? AccountState.SignedIn)?.name.orEmpty()

    var role by remember {
        mutableStateOf(if (profile.role == UserRole.UNKNOWN) UserRole.STUDENT else profile.role)
    }
    var name by remember { mutableStateOf(profile.displayName.ifBlank { signedInName }) }
    var school by remember { mutableStateOf(profile.school) }
    var subject by remember { mutableStateOf(profile.subject) }
    var bio by remember { mutableStateOf(profile.bio) }
    var photo by remember { mutableStateOf(profile.photo) }

    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var pictureTooBig by remember { mutableStateOf(false) }

    val pickPicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pictureTooBig = false
            scope.launch {
                val encoded = ProfilePicture.read(context, uri)
                if (encoded == null) pictureTooBig = true else photo = encoded
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(
                        photo = photo,
                        name = name,
                        size = 72.dp,
                        modifier = Modifier.clickable { pickPicture.launch("image/*") }
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_picture),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pickPicture.launch("image/*") }) {
                                Text(stringResource(R.string.change_picture))
                            }
                            if (photo.isNotBlank()) {
                                TextButton(onClick = { photo = "" }) {
                                    Text(stringResource(R.string.remove_picture))
                                }
                            }
                        }
                        if (pictureTooBig) {
                            Text(
                                text = stringResource(R.string.picture_too_big),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = role == UserRole.STUDENT,
                    onClick = { role = UserRole.STUDENT },
                    label = { Text("🎒 " + stringResource(R.string.role_student)) },
                    shape = RoundedCornerShape(14.dp)
                )
                FilterChip(
                    selected = role == UserRole.TEACHER,
                    onClick = { role = UserRole.TEACHER },
                    label = { Text("🍎 " + stringResource(R.string.role_teacher)) },
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; saved = false },
                label = { Text(stringResource(R.string.your_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(280); saved = false },
                label = { Text(stringResource(R.string.your_bio)) },
                placeholder = { Text(stringResource(R.string.bio_hint)) },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = school,
                onValueChange = { school = it; saved = false },
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
                    onValueChange = { subject = it; saved = false },
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (saved) {
            item {
                Text(
                    text = "✓ " + stringResource(R.string.profile_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Button(
                onClick = {
                    if (name.isBlank() || busy) return@Button
                    busy = true
                    failed = false
                    saved = false
                    scope.launch {
                        val result = classroom.saveProfile(
                            role = role,
                            displayName = name,
                            school = school,
                            subject = subject,
                            bio = bio,
                            photo = photo
                        )
                        if (result.isSuccess) cloud.updateDisplayName(name)
                        busy = false
                        if (result.isSuccess) saved = true else failed = true
                    }
                },
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.save_changes))
            }
        }

        item { PasswordSection(cloud = cloud) }

        item {
            TextButton(
                onClick = onBack,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

/**
 * Only accounts that actually have a password get the form. A Google account
 * has none to change, so it is told where its password really lives instead of
 * being offered a control that could only fail.
 */
@Composable
private fun PasswordSection(cloud: CloudSync) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    Column {
        SectionHeader(title = "🔒 " + stringResource(R.string.change_password))
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!cloud.hasPassword) {
                    Text(
                        text = stringResource(R.string.password_google_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@Column
                }

                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; done = false; errorRes = null },
                    label = { Text(stringResource(R.string.current_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it; done = false; errorRes = null },
                    label = { Text(stringResource(R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it; done = false; errorRes = null },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val message = errorRes
                if (message != null) {
                    Text(
                        text = stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (done) {
                    Text(
                        text = "✓ " + stringResource(R.string.password_changed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        if (busy) return@Button
                        errorRes = null
                        done = false
                        if (replacement.length < 6) {
                            errorRes = R.string.error_password_short
                            return@Button
                        }
                        if (replacement != confirmation) {
                            errorRes = R.string.passwords_dont_match
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val result = cloud.changePassword(current, replacement)
                            busy = false
                            if (result.isSuccess) {
                                current = ""
                                replacement = ""
                                confirmation = ""
                                done = true
                            } else {
                                errorRes = authErrorMessage(result.exceptionOrNull())
                            }
                        }
                    },
                    enabled = !busy && current.isNotBlank() && replacement.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.change_password))
                }
            }
        }
    }
}
