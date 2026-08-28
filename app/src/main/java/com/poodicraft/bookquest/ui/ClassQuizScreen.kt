package com.poodicraft.bookquest.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.Assignment
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * A quiz a teacher set, taken by a student. The score goes back to the class so
 * the teacher can see it, and also counts towards the student's own XP.
 */
@Composable
fun ClassQuizScreen(
    classId: String?,
    assignmentId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val classroom = remember { Classroom.get(context) }
    val library = remember { LibraryRepository.get(context) }
    val scope = rememberCoroutineScope()

    var assignment by remember { mutableStateOf<Assignment?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(classId, assignmentId) {
        if (classId == null || assignmentId == null) {
            loading = false
            return@LaunchedEffect
        }
        assignment = classroom.assignments(classId).getOrElse { emptyList() }
            .firstOrNull { it.id == assignmentId }
        loading = false
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val quiz = assignment
    if (quiz == null || quiz.questions.isEmpty() || classId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                emoji = "🧠",
                title = stringResource(R.string.no_cards),
                message = stringResource(R.string.something_failed),
                action = { Button(onClick = onBack) { Text(stringResource(R.string.back)) } }
            )
        }
        return
    }

    TypedQuizRunner(
        title = quiz.title,
        questions = quiz.questions,
        onBack = onBack,
        onFinish = { correct, total ->
            library.recordQuiz(correct, total)
            scope.launch { classroom.submitResult(classId, quiz, correct, total) }
        }
    )
}
