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
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.Assignment
import com.poodicraft.bookquest.data.ClassMember
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

private enum class DetailMode { OVERVIEW, SET_BOOK, WRITE_QUIZ }

private data class QuestionDraft(
    val prompt: String = "",
    val answer: String = "",
    val alternative: String = ""
)

/** A teacher's view of one class: who is in it, what is set, and how it went. */
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
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(DetailMode.OVERVIEW) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var schoolClass by remember { mutableStateOf<SchoolClass?>(null) }
    var members by remember { mutableStateOf<List<ClassMember>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var results by remember { mutableStateOf<List<QuizResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(classId, refreshKey) {
        loading = true
        schoolClass = classroom.myClasses().getOrElse { emptyList() }
            .firstOrNull { it.id == classId }
        members = classroom.members(classId).getOrElse { emptyList() }
        assignments = classroom.assignments(classId).getOrElse { emptyList() }
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
                            if (mode == DetailMode.OVERVIEW) onBack() else mode = DetailMode.OVERVIEW
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

                else -> Overview(
                    schoolClass = schoolClass,
                    members = members,
                    assignments = assignments,
                    results = results,
                    onSetBook = { mode = DetailMode.SET_BOOK },
                    onWriteQuiz = { mode = DetailMode.WRITE_QUIZ },
                    onDelete = { assignment ->
                        scope.launch {
                            classroom.deleteAssignment(classId, assignment.id)
                            refreshKey += 1
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Overview(
    schoolClass: SchoolClass?,
    members: List<ClassMember>,
    assignments: List<Assignment>,
    results: List<QuizResult>,
    onSetBook: () -> Unit,
    onWriteQuiz: () -> Unit,
    onDelete: (Assignment) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Brand.VioletDeep, Brand.Violet)))
                    .padding(22.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.class_code),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = schoolClass?.joinCode.orEmpty(),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.share_code_hint),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(schoolClass?.joinCode.orEmpty()))
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

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSetBook,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("📘 " + stringResource(R.string.assign_book))
                }
                Button(
                    onClick = onWriteQuiz,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("🧠 " + stringResource(R.string.new_quiz))
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
                    Text(text = "🎒", fontSize = 20.sp)
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

        item {
            SectionHeader(
                title = stringResource(R.string.assignments),
                trailing = {
                    Text(
                        text = stringResource(R.string.assignments_count, assignments.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        if (assignments.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_assignments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text(text = if (assignment.isQuiz) "🧠" else "📘", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = assignment.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (assignment.isQuiz) {
                                stringResource(
                                    R.string.questions_count,
                                    assignment.questions.size
                                )
                            } else {
                                assignment.author.ifBlank {
                                    stringResource(Subject.fromId(assignment.subjectId).labelRes)
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

        item { SectionHeader(title = stringResource(R.string.results_title)) }

        if (results.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_quiz_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(results, key = { it.studentUid + it.assignmentId }) { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
    var tooBig by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        String(stream.readBytes(), Charsets.UTF_8)
                    }.orEmpty()
                } catch (e: Exception) {
                    ""
                }
            }
            if (text.length > Assignment.MAX_INLINE_CHARS) {
                tooBig = true
                content = ""
            } else {
                tooBig = false
                content = text
                if (title.isBlank()) title = "?"
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
                            text = if (content.isNotBlank()) {
                                stringResource(R.string.attached_ready, content.length)
                            } else {
                                stringResource(R.string.attach_text)
                            }
                        )
                    }
                    if (tooBig) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.attach_too_big),
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
                            classId, title, author, subject, note, content
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
