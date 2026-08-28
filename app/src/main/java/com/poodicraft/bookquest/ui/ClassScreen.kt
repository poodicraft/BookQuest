package com.poodicraft.bookquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poodicraft.bookquest.R
import android.util.Base64
import com.poodicraft.bookquest.data.AccountState
import com.poodicraft.bookquest.data.Assignment
import com.poodicraft.bookquest.data.BookFormat
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.data.SchoolClass
import com.poodicraft.bookquest.data.Subject
import com.poodicraft.bookquest.data.UserRole
import com.poodicraft.bookquest.ui.components.EmptyState
import com.poodicraft.bookquest.ui.components.SectionHeader
import com.poodicraft.bookquest.ui.theme.Brand
import kotlinx.coroutines.launch

/**
 * The Class tab. What it shows depends on who is looking: a teacher gets their
 * classes, a student gets the work set for theirs.
 */
@Composable
fun ClassScreen(
    onOpenClass: (String) -> Unit,
    onRunQuiz: (String, String) -> Unit,
    onSignIn: () -> Unit
) {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context) }
    val classroom = remember { Classroom.get(context) }
    val library = remember { LibraryRepository.get(context) }
    val scope = rememberCoroutineScope()

    val account by cloud.account.collectAsStateWithLifecycle()
    val profile by classroom.profile.collectAsStateWithLifecycle()

    var refreshKey by remember { mutableIntStateOf(0) }
    val classes by classroom.classes.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(classes.isEmpty()) }

    val signedIn = account is AccountState.SignedIn

    LaunchedEffect(signedIn, refreshKey) {
        if (!signedIn) {
            loading = false
            return@LaunchedEffect
        }
        // Only block on the spinner when there is nothing cached to show.
        loading = classes.isEmpty()
        classroom.ensureLoaded(force = refreshKey > 0)
        loading = false
    }

    if (!signedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                emoji = "🏫",
                title = stringResource(R.string.sign_in_required),
                message = stringResource(R.string.sign_in_required_hint),
                action = {
                    Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in_title)) }
                }
            )
        }
        return
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (profile.role == UserRole.UNKNOWN) {
        RoleScreen(onDone = { refreshKey += 1 })
        return
    }

    if (profile.role == UserRole.TEACHER) {
        TeacherClasses(
            classes = classes,
            classroom = classroom,
            school = profile.school,
            onOpenClass = onOpenClass,
            onChanged = { refreshKey += 1 }
        )
    } else {
        StudentClasses(
            classes = classes,
            classroom = classroom,
            library = library,
            onRunQuiz = onRunQuiz,
            onOpenClass = onOpenClass,
            onJoined = { refreshKey += 1 }
        )
    }
}

// ------------------------------------------------------------------ teacher

@Composable
private fun TeacherClasses(
    classes: List<SchoolClass>,
    classroom: Classroom,
    school: String,
    onOpenClass: (String) -> Unit,
    onChanged: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.my_classes),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (creating) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.class_name)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { creating = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                onClick = {
                                    if (name.isBlank() || busy) return@Button
                                    busy = true
                                    scope.launch {
                                        val result = classroom.createClass(name, school)
                                        busy = false
                                        if (result.isSuccess) {
                                            name = ""
                                            creating = false
                                            onChanged()
                                        }
                                    }
                                },
                                enabled = name.isNotBlank() && !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.create_class))
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Button(
                    onClick = { creating = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_class))
                }
            }
        }

        if (classes.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🏫",
                    title = stringResource(R.string.no_classes_teacher),
                    message = stringResource(R.string.no_classes_teacher_hint)
                )
            }
        }

        items(classes, key = { it.id }) { schoolClass ->
            ClassRow(schoolClass = schoolClass, onClick = { onOpenClass(schoolClass.id) })
        }
    }
}

@Composable
private fun ClassRow(schoolClass: SchoolClass, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(Brand.Violet, Brand.Sky))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🏫", fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schoolClass.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (schoolClass.school.isNotBlank()) {
                    Text(
                        text = schoolClass.school,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = schoolClass.joinCode,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ------------------------------------------------------------------ student

@Composable
private fun StudentClasses(
    classes: List<SchoolClass>,
    classroom: Classroom,
    library: LibraryRepository,
    onRunQuiz: (String, String) -> Unit,
    onOpenClass: (String) -> Unit,
    onJoined: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var assignments by remember { mutableStateOf<Map<String, List<Assignment>>>(emptyMap()) }
    LaunchedEffect(classes) {
        val gathered = HashMap<String, List<Assignment>>()
        for (item in classes) {
            gathered[item.id] = classroom.assignments(item.id).getOrElse { emptyList() }
        }
        assignments = gathered
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_class),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.join_class),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.no_class_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = {
                                code = it.uppercase().take(8)
                                codeError = false
                            },
                            label = { Text(stringResource(R.string.join_code_label)) },
                            singleLine = true,
                            isError = codeError,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (busy || code.isBlank()) return@Button
                                busy = true
                                codeError = false
                                scope.launch {
                                    val result = classroom.joinClass(code)
                                    busy = false
                                    if (result.isSuccess) {
                                        code = ""
                                        onJoined()
                                    } else {
                                        codeError = true
                                    }
                                }
                            },
                            enabled = code.isNotBlank() && !busy,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.join_action))
                        }
                    }
                    if (codeError) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.bad_code),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (classes.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🎒",
                    title = stringResource(R.string.no_class_yet),
                    message = stringResource(R.string.no_class_hint)
                )
            }
        }

        for (schoolClass in classes) {
            item(key = "head-" + schoolClass.id) {
                Column(modifier = Modifier.clickable { onOpenClass(schoolClass.id) }) {
                    SectionHeader(title = schoolClass.name + "  ›")
                    if (schoolClass.teacherName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.teacher_label) + ": " +
                                schoolClass.teacherName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val forClass = assignments[schoolClass.id].orEmpty()
            if (forClass.isEmpty()) {
                item(key = "empty-" + schoolClass.id) {
                    Text(
                        text = stringResource(R.string.no_assignments),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(forClass, key = { it.id }) { assignment ->
                StudentAssignmentCard(
                    assignment = assignment,
                    alreadyOwned = library.hasBookTitled(assignment.title),
                    onStartQuiz = { onRunQuiz(schoolClass.id, assignment.id) },
                    onOpenClass = { onOpenClass(schoolClass.id) },
                    onAddBook = {
                        val subject = Subject.fromId(assignment.subjectId)
                        if (assignment.contentBase64.isNotBlank()) {
                            val bytes = try {
                                Base64.decode(assignment.contentBase64, Base64.NO_WRAP)
                            } catch (e: Exception) {
                                ByteArray(0)
                            }
                            if (bytes.isNotEmpty()) {
                                library.addBinaryBook(
                                    assignment.title,
                                    assignment.author,
                                    subject,
                                    BookFormat.fromId(assignment.contentFormat),
                                    bytes
                                )
                            }
                        } else {
                            library.addTextBook(
                                assignment.title,
                                assignment.author,
                                subject,
                                assignment.content
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StudentAssignmentCard(
    assignment: Assignment,
    alreadyOwned: Boolean,
    onStartQuiz: () -> Unit,
    onOpenClass: () -> Unit,
    onAddBook: () -> Unit
) {
    var added by remember(assignment.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        assignment.isQuiz -> "🧠"
                        assignment.isHomework -> "📝"
                        else -> "📘"
                    },
                    fontSize = 24.sp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignment.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            assignment.isQuiz -> stringResource(
                                R.string.questions_count,
                                assignment.questions.size
                            )

                            assignment.isHomework -> if (assignment.dueAt > 0L) {
                                stringResource(R.string.due_on, "")
                            } else {
                                stringResource(R.string.due_none)
                            }

                            else -> assignment.author.ifBlank {
                                stringResource(Subject.fromId(assignment.subjectId).labelRes)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (assignment.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = assignment.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            if (assignment.isHomework) {
                Button(
                    onClick = onOpenClass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.open_homework))
                }
            } else if (assignment.isQuiz) {
                Button(
                    onClick = onStartQuiz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.quiz_start))
                }
            } else if (assignment.content.isNotBlank() ||
                assignment.contentBase64.isNotBlank()
            ) {
                Button(
                    onClick = {
                        onAddBook()
                        added = true
                    },
                    enabled = !added && !alreadyOwned,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        stringResource(
                            if (added || alreadyOwned) R.string.added_to_library
                            else R.string.add_to_library
                        )
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.no_books_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Kept for the shared chip look used by the class detail screen. */
@Composable
internal fun CodeChip(code: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(text = code, fontWeight = FontWeight.Bold, color = Color.Unspecified)
        },
        shape = RoundedCornerShape(14.dp)
    )
}
