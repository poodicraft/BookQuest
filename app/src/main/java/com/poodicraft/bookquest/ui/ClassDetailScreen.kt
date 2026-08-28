package com.poodicraft.bookquest.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poodicraft.bookquest.R
import android.util.Base64
import com.poodicraft.bookquest.data.Assignment
import com.poodicraft.bookquest.data.BookFormat
import com.poodicraft.bookquest.data.ClassMember
import com.poodicraft.bookquest.data.ClassPost
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.data.Submission
import com.poodicraft.bookquest.data.UserRole
import com.poodicraft.bookquest.data.FileType
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.QuizQuestion
import com.poodicraft.bookquest.data.QuizResult
import com.poodicraft.bookquest.data.SchoolClass
import com.poodicraft.bookquest.data.Subject
import com.poodicraft.bookquest.ui.components.EmptyState
import com.poodicraft.bookquest.ui.components.SectionHeader
import com.poodicraft.bookquest.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DetailMode { OVERVIEW, SET_BOOK, WRITE_QUIZ, SET_HOMEWORK, HOMEWORK }

private enum class DetailTab { STREAM, WORK, PEOPLE }

private class AttachedFile(val format: BookFormat, val bytes: ByteArray)

private data class QuestionDraft(
    val prompt: String = "",
    val answer: String = "",
    val alternative: String = ""
)

private fun shortDate(millis: Long): String = try {
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
} catch (e: Exception) {
    ""
}

/**
 * One class, for whoever is looking at it. A teacher gets the code to hand out
 * and every control for setting work; a student gets the same stream and the
 * same list of work, and hands their homework in from it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(classId: String?, onBack: () -> Unit) {
    if (classId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                emoji = "🏫",
                title = stringResource(R.string.no_classes_teacher),
                message = stringResource(R.string.no_classes_teacher_hint),
                action = { Button(onClick = onBack) { Text(stringResource(R.string.back)) } }
            )
        }
        return
    }

    val context = LocalContext.current
    val classroom = remember { Classroom.get(context) }
    val library = remember { LibraryRepository.get(context) }
    val scope = rememberCoroutineScope()

    val profile by classroom.profile.collectAsStateWithLifecycle()
    val isTeacher = profile.role == UserRole.TEACHER

    var mode by remember { mutableStateOf(DetailMode.OVERVIEW) }
    var tab by remember { mutableStateOf(DetailTab.STREAM) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var openHomework by remember { mutableStateOf<Assignment?>(null) }

    var schoolClass by remember { mutableStateOf<SchoolClass?>(null) }
    var members by remember { mutableStateOf<List<ClassMember>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var posts by remember { mutableStateOf<List<ClassPost>>(emptyList()) }
    var results by remember { mutableStateOf<List<QuizResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(classId, refreshKey) {
        loading = true
        schoolClass = classroom.classes.value.firstOrNull { it.id == classId }
            ?: classroom.myClasses().getOrElse { emptyList() }.firstOrNull { it.id == classId }
        members = classroom.members(classId).getOrElse { emptyList() }
        assignments = classroom.assignments(classId).getOrElse { emptyList() }
        posts = classroom.posts(classId).getOrElse { emptyList() }
        results = classroom.results(classId).getOrElse { emptyList() }
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(schoolClass?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (mode == DetailMode.OVERVIEW) {
                                onBack()
                            } else {
                                mode = DetailMode.OVERVIEW
                                openHomework = null
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val homework = openHomework
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                mode == DetailMode.SET_BOOK -> SetBookForm(
                    classroom = classroom,
                    classId = classId,
                    onDone = {
                        mode = DetailMode.OVERVIEW
                        refreshKey += 1
                    }
                )

                mode == DetailMode.WRITE_QUIZ -> WriteQuizForm(
                    classroom = classroom,
                    classId = classId,
                    onDone = {
                        mode = DetailMode.OVERVIEW
                        refreshKey += 1
                    }
                )

                mode == DetailMode.SET_HOMEWORK -> SetHomeworkForm(
                    classroom = classroom,
                    classId = classId,
                    onDone = {
                        mode = DetailMode.OVERVIEW
                        refreshKey += 1
                    }
                )

                mode == DetailMode.HOMEWORK && homework != null -> HomeworkDetail(
                    classroom = classroom,
                    classId = classId,
                    assignment = homework,
                    isTeacher = isTeacher,
                    memberCount = members.size,
                    onChanged = { refreshKey += 1 }
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    TabRowChips(tab = tab, onTab = { tab = it })
                    when (tab) {
                        DetailTab.STREAM -> StreamTab(
                            classroom = classroom,
                            library = library,
                            classId = classId,
                            schoolClass = schoolClass,
                            posts = posts,
                            isTeacher = isTeacher,
                            onChanged = { refreshKey += 1 }
                        )

                        DetailTab.WORK -> WorkTab(
                            assignments = assignments,
                            results = results,
                            isTeacher = isTeacher,
                            onSetBook = { mode = DetailMode.SET_BOOK },
                            onWriteQuiz = { mode = DetailMode.WRITE_QUIZ },
                            onSetHomework = { mode = DetailMode.SET_HOMEWORK },
                            onOpenHomework = {
                                openHomework = it
                                mode = DetailMode.HOMEWORK
                            },
                            onDelete = { assignment ->
                                scope.launch {
                                    classroom.deleteAssignment(classId, assignment.id)
                                    refreshKey += 1
                                }
                            }
                        )

                        DetailTab.PEOPLE -> PeopleTab(
                            schoolClass = schoolClass,
                            members = members
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRowChips(tab: DetailTab, onTab: (DetailTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            DetailTab.STREAM to R.string.tab_stream,
            DetailTab.WORK to R.string.tab_work,
            DetailTab.PEOPLE to R.string.tab_people
        ).forEach { (value, label) ->
            FilterChip(
                selected = tab == value,
                onClick = { onTab(value) },
                label = { Text(stringResource(label)) },
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

// ------------------------------------------------------------------- stream

@Composable
private fun StreamTab(
    classroom: Classroom,
    library: LibraryRepository,
    classId: String,
    schoolClass: SchoolClass?,
    posts: List<ClassPost>,
    isTeacher: Boolean,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var text by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    var tooBig by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = readAttachment(context, uri)
            if (loaded == null || !loaded.fitsInline) {
                tooBig = loaded != null
                attachment = null
            } else {
                tooBig = false
                attachment = loaded
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isTeacher) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(Brand.VioletDeep, Brand.Violet))
                        )
                        .padding(18.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.class_code),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = schoolClass?.joinCode.orEmpty(),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(schoolClass?.joinCode.orEmpty())
                                )
                                copied = true
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (copied) R.string.code_copied else R.string.copy_code
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.write_something)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val picked = attachment
                    if (picked != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📎 " + picked.name + "  ·  " + picked.kilobytes + " KB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { attachment = null }) {
                                Text(stringResource(R.string.remove_attachment))
                            }
                        }
                    }
                    if (tooBig) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.file_too_big),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📎 " + stringResource(R.string.attach_action))
                        }
                        Button(
                            onClick = {
                                if (busy) return@Button
                                val current = attachment
                                busy = true
                                scope.launch {
                                    val result = classroom.addPost(
                                        classId,
                                        text,
                                        current?.name.orEmpty(),
                                        current?.format?.id.orEmpty(),
                                        current?.encode().orEmpty()
                                    )
                                    busy = false
                                    if (result.isSuccess) {
                                        text = ""
                                        attachment = null
                                        onChanged()
                                    }
                                }
                            },
                            enabled = !busy && (text.isNotBlank() || attachment != null),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.post_action))
                        }
                    }
                }
            }
        }

        if (posts.isEmpty()) {
            item {
                EmptyState(
                    emoji = "💬",
                    title = stringResource(R.string.no_posts),
                    message = stringResource(R.string.no_posts_hint)
                )
            }
        }

        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                canDelete = isTeacher || post.authorUid == classroom.uid,
                onSaveFile = {
                    val bytes = decodeAttachment(post.fileBase64)
                    if (bytes.isNotEmpty()) {
                        library.addBinaryBook(
                            post.fileName.substringBeforeLast('.', post.fileName),
                            post.authorName,
                            Subject.GENERAL,
                            BookFormat.fromId(post.fileFormat),
                            bytes
                        )
                    }
                },
                onDelete = {
                    scope.launch {
                        classroom.deletePost(classId, post.id)
                        onChanged()
                    }
                }
            )
        }
    }
}

@Composable
private fun PostCard(
    post: ClassPost,
    canDelete: Boolean,
    onSaveFile: () -> Unit,
    onDelete: () -> Unit
) {
    var saved by remember(post.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (post.authorRole == UserRole.TEACHER.id) "🍎" else "🎒",
                    fontSize = 18.sp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = shortDate(post.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (post.fileBase64.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📎 " + post.fileName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (BookFormat.fromId(post.fileFormat) != BookFormat.UNKNOWN) {
                        TextButton(
                            onClick = {
                                onSaveFile()
                                saved = true
                            },
                            enabled = !saved
                        ) {
                            Text(
                                stringResource(
                                    if (saved) R.string.added_to_library
                                    else R.string.save_to_library
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- work

@Composable
private fun WorkTab(
    assignments: List<Assignment>,
    results: List<QuizResult>,
    isTeacher: Boolean,
    onSetBook: () -> Unit,
    onWriteQuiz: () -> Unit,
    onSetHomework: () -> Unit,
    onOpenHomework: (Assignment) -> Unit,
    onDelete: (Assignment) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isTeacher) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSetBook,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("📘")
                    }
                    Button(
                        onClick = onWriteQuiz,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("🧠")
                    }
                    Button(
                        onClick = onSetHomework,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("📝")
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.assign_book) + " · " +
                        stringResource(R.string.new_quiz) + " · " +
                        stringResource(R.string.set_homework),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (assignments.isEmpty()) {
            item {
                EmptyState(
                    emoji = "📋",
                    title = stringResource(R.string.no_assignments),
                    message = stringResource(R.string.no_class_hint)
                )
            }
        }

        items(assignments, key = { it.id }) { assignment ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            assignment.isQuiz -> "🧠"
                            assignment.isHomework -> "📝"
                            else -> "📘"
                        },
                        fontSize = 20.sp
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
                                    stringResource(R.string.due_on, shortDate(assignment.dueAt))
                                } else {
                                    stringResource(R.string.due_none)
                                }

                                else -> assignment.author.ifBlank {
                                    stringResource(
                                        Subject.fromId(assignment.subjectId).labelRes
                                    )
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (assignment.isHomework) {
                        TextButton(onClick = { onOpenHomework(assignment) }) {
                            Text(stringResource(R.string.open_homework))
                        }
                    }
                    if (isTeacher) {
                        IconButton(onClick = { onDelete(assignment) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete_item),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isTeacher && results.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.results_title)) }
            items(results, key = { it.studentUid + it.assignmentId }) { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.studentName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = result.assignmentTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = result.correct.toString() + " / " + result.total,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.correct == result.total) Brand.Mint
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------- people

@Composable
private fun PeopleTab(schoolClass: SchoolClass?, members: List<ClassMember>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🍎", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = schoolClass?.teacherName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.teacher_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.roster),
                trailing = {
                    Text(
                        text = stringResource(R.string.students_count, members.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        if (members.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_students),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(members, key = { it.uid }) { member ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🎒", fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.name.ifBlank { member.email },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (member.email.isNotBlank()) {
                            Text(
                                text = member.email,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- homework

@Composable
private fun SetHomeworkForm(
    classroom: Classroom,
    classId: String,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(7) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.set_homework),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.homework_title)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text(stringResource(R.string.homework_instructions)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (days <= 0) {
                            stringResource(R.string.due_none)
                        } else {
                            stringResource(R.string.due_in_days, days)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = days.toFloat(),
                        onValueChange = { days = it.toInt() },
                        valueRange = 0f..30f,
                        steps = 29
                    )
                }
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
                    if (title.isBlank() || busy) return@Button
                    busy = true
                    failed = false
                    val due = if (days <= 0) 0L else
                        System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                    scope.launch {
                        val result = classroom.assignHomework(classId, title, instructions, due)
                        busy = false
                        if (result.isSuccess) onDone() else failed = true
                    }
                },
                enabled = title.isNotBlank() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.give_homework))
            }
        }
    }
}

@Composable
private fun HomeworkDetail(
    classroom: Classroom,
    classId: String,
    assignment: Assignment,
    isTeacher: Boolean,
    memberCount: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var handIns by remember { mutableStateOf<List<Submission>>(emptyList()) }
    var mine by remember { mutableStateOf<Submission?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(assignment.id, reload) {
        loading = true
        if (isTeacher) {
            handIns = classroom.submissions(classId, assignment.id).getOrElse { emptyList() }
        } else {
            mine = classroom.mySubmission(classId, assignment.id)
        }
        loading = false
    }

    var answer by remember(mine) { mutableStateOf(mine?.text.orEmpty()) }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    var tooBig by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = readAttachment(context, uri)
            if (loaded == null || !loaded.fitsInline) {
                tooBig = loaded != null
                attachment = null
            } else {
                tooBig = false
                attachment = loaded
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "📝  " + assignment.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (assignment.dueAt > 0L) {
                        stringResource(R.string.due_on, shortDate(assignment.dueAt))
                    } else {
                        stringResource(R.string.due_none)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (assignment.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = assignment.note,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (isTeacher) {
            item {
                SectionHeader(
                    title = stringResource(R.string.submissions_title),
                    trailing = {
                        Text(
                            text = stringResource(
                                R.string.submissions_count,
                                handIns.size,
                                memberCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            if (handIns.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.not_handed_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(handIns, key = { it.studentUid }) { submission ->
                MarkingCard(
                    submission = submission,
                    onSave = { grade, feedback ->
                        scope.launch {
                            classroom.gradeSubmission(
                                classId, assignment.id, submission.studentUid, grade, feedback
                            )
                            reload += 1
                            onChanged()
                        }
                    }
                )
            }
        } else {
            val current = mine
            item {
                Text(
                    text = if (current == null) {
                        stringResource(R.string.not_handed_in)
                    } else {
                        stringResource(R.string.handed_in, shortDate(current.submittedAt))
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else Brand.Mint
                )
            }

            if (current != null && current.isGraded) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.marked_label, current.grade),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (current.feedback.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = current.feedback,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text(stringResource(R.string.your_answer)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val picked = attachment
                Column {
                    if (picked != null) {
                        Text(
                            text = "📎 " + picked.name + "  ·  " + picked.kilobytes + " KB",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    } else if (current != null && current.fileName.isNotBlank()) {
                        Text(
                            text = "📎 " + current.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (tooBig) {
                        Text(
                            text = stringResource(R.string.file_too_big),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📎 " + stringResource(R.string.attach_action))
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (busy) return@Button
                        val picked = attachment
                        busy = true
                        scope.launch {
                            classroom.submitHomework(
                                classId,
                                assignment.id,
                                answer,
                                picked?.name ?: current?.fileName.orEmpty(),
                                picked?.format?.id ?: current?.fileFormat.orEmpty(),
                                picked?.encode() ?: current?.fileBase64.orEmpty()
                            )
                            busy = false
                            attachment = null
                            reload += 1
                            onChanged()
                        }
                    },
                    enabled = !busy && (answer.isNotBlank() || attachment != null),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        stringResource(
                            if (current == null) R.string.hand_in else R.string.hand_in_again
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkingCard(
    submission: Submission,
    onSave: (String, String) -> Unit
) {
    var grade by remember(submission.studentUid) { mutableStateOf(submission.grade) }
    var feedback by remember(submission.studentUid) { mutableStateOf(submission.feedback) }
    var saved by remember(submission.studentUid) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎒", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = submission.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.handed_in,
                            shortDate(submission.submittedAt)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (submission.isGraded) {
                    Text(text = "✅", fontSize = 18.sp)
                }
            }

            if (submission.text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = submission.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (submission.fileName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "📎 " + submission.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = grade,
                    onValueChange = {
                        grade = it
                        saved = false
                    },
                    label = { Text(stringResource(R.string.grade_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(110.dp)
                )
                OutlinedTextField(
                    value = feedback,
                    onValueChange = {
                        feedback = it
                        saved = false
                    },
                    label = { Text(stringResource(R.string.feedback_label)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onSave(grade, feedback)
                    saved = true
                },
                enabled = !saved && grade.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.save_mark))
            }
        }
    }
}

// ------------------------------------------------------------------ set book

@Composable
private fun SetBookForm(
    classroom: Classroom,
    classId: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Subject.GENERAL) }
    var content by remember { mutableStateOf("") }
    var contentBase64 by remember { mutableStateOf("") }
    var contentFormat by remember { mutableStateOf("") }
    var attachedBytes by remember { mutableIntStateOf(0) }
    var tooBig by remember { mutableStateOf(false) }
    var unreadable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val mime = context.contentResolver.getType(uri)
                    val bytes = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() } ?: return@withContext null
                    val head = bytes.copyOf(minOf(bytes.size, FileType.HEAD_BYTES))
                    AttachedFile(FileType.detect(null, mime, head), bytes)
                } catch (e: Exception) {
                    null
                }
            }

            tooBig = false
            unreadable = false
            content = ""
            contentBase64 = ""
            contentFormat = ""
            attachedBytes = 0

            when {
                loaded == null || loaded.format == BookFormat.UNKNOWN -> unreadable = true

                // Text formats travel as text so they stay searchable and small.
                loaded.format == BookFormat.TXT || loaded.format == BookFormat.HTML -> {
                    val text = String(loaded.bytes, Charsets.UTF_8)
                    if (text.length > Assignment.MAX_INLINE_CHARS) {
                        tooBig = true
                    } else {
                        content = text
                        attachedBytes = loaded.bytes.size
                    }
                }

                // Everything else goes whole, encoded into the document.
                loaded.bytes.size > Assignment.MAX_INLINE_BYTES -> tooBig = true

                else -> {
                    contentBase64 = Base64.encodeToString(loaded.bytes, Base64.NO_WRAP)
                    contentFormat = loaded.format.id
                    attachedBytes = loaded.bytes.size
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.assign_book),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.field_author)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                text = stringResource(R.string.field_subject),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Subject.values().toList(), key = { it.id }) { option ->
                    FilterChip(
                        selected = subject == option,
                        onClick = { subject = option },
                        label = { Text(option.emoji + " " + stringResource(option.labelRes)) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.book_note)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.attach_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (attachedBytes > 0) {
                                stringResource(
                                    R.string.attached_ready,
                                    (attachedBytes / 1024).coerceAtLeast(1)
                                )
                            } else {
                                stringResource(R.string.attach_text)
                            }
                        )
                    }
                    if (tooBig || unreadable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                if (tooBig) R.string.attach_too_big
                                else R.string.reader_error
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
                    if (title.isBlank() || busy) return@Button
                    busy = true
                    failed = false
                    scope.launch {
                        val result = classroom.assignBook(
                            classId, title, author, subject, note, content,
                            contentBase64, contentFormat
                        )
                        busy = false
                        if (result.isSuccess) onDone() else failed = true
                    }
                },
                enabled = title.isNotBlank() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.assign_book))
            }
        }
    }
}

// ---------------------------------------------------------------- quiz maker

@Composable
private fun WriteQuizForm(
    classroom: Classroom,
    classId: String,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf(listOf(QuestionDraft())) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val ready = title.isNotBlank() &&
        drafts.any { it.prompt.isNotBlank() && it.answer.isNotBlank() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.new_quiz),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.quiz_title_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(drafts.size) { position ->
            val draft = drafts[position]
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.question_label, position + 1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (drafts.size > 1) {
                            IconButton(
                                onClick = {
                                    drafts = drafts.filterIndexed { i, _ -> i != position }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.delete_item),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.prompt,
                        onValueChange = { value ->
                            drafts = drafts.mapIndexed { i, item ->
                                if (i == position) item.copy(prompt = value) else item
                            }
                        },
                        label = { Text(stringResource(R.string.card_front)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.answer,
                        onValueChange = { value ->
                            drafts = drafts.mapIndexed { i, item ->
                                if (i == position) item.copy(answer = value) else item
                            }
                        },
                        label = { Text(stringResource(R.string.answer_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.alternative,
                        onValueChange = { value ->
                            drafts = drafts.mapIndexed { i, item ->
                                if (i == position) item.copy(alternative = value) else item
                            }
                        },
                        label = { Text(stringResource(R.string.alt_answer_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { drafts = drafts + QuestionDraft() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_question))
            }
        }

        if (!ready) {
            item {
                Text(
                    text = stringResource(R.string.need_question),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (!ready || busy) return@Button
                    busy = true
                    failed = false
                    val questions = drafts
                        .filter { it.prompt.isNotBlank() && it.answer.isNotBlank() }
                        .map { draft ->
                            QuizQuestion(
                                prompt = draft.prompt,
                                answer = draft.answer,
                                alternatives = draft.alternative
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                            )
                        }
                    scope.launch {
                        val result = classroom.assignQuiz(classId, title, questions)
                        busy = false
                        if (result.isSuccess) onDone() else failed = true
                    }
                },
                enabled = ready && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.save_quiz))
            }
        }
    }
}
