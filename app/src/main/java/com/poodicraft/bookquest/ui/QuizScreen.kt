package com.poodicraft.bookquest.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.AnswerCheck
import com.poodicraft.bookquest.data.Book
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.data.QuizQuestion
import com.poodicraft.bookquest.ui.components.ConfettiBurst
import com.poodicraft.bookquest.ui.components.EmptyState
import com.poodicraft.bookquest.ui.theme.Brand

/** The personal flashcard quiz, built from the cards on one book. */
@Composable
fun QuizScreen(
    book: Book?,
    repository: LibraryRepository,
    onBack: () -> Unit
) {
    if (book == null || book.cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                emoji = "🧠",
                title = stringResource(R.string.no_cards),
                message = stringResource(R.string.no_cards_hint),
                action = { Button(onClick = onBack) { Text(stringResource(R.string.back)) } }
            )
        }
        return
    }

    val questions = remember(book.id, book.cards.size) {
        book.cards.map { QuizQuestion(prompt = it.front, answer = it.back) }.shuffled()
    }

    TypedQuizRunner(
        title = book.title,
        questions = questions,
        onBack = onBack,
        onFinish = { correct, total -> repository.recordQuiz(correct, total) }
    )
}

/**
 * Asks each question and marks what the student types. There is deliberately no
 * "I knew it" button: writing the answer down is the part that proves you
 * recalled it, and self marking quietly turns a test back into a review.
 *
 * Marking is forgiving rather than exact — see [AnswerCheck].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypedQuizRunner(
    title: String,
    questions: List<QuizQuestion>,
    onBack: () -> Unit,
    onFinish: (Int, Int) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    var round by remember { mutableIntStateOf(0) }
    var index by remember(round) { mutableIntStateOf(0) }
    var typed by remember(round) { mutableStateOf("") }
    var judged by remember(round) { mutableStateOf<Boolean?>(null) }
    var correct by remember(round) { mutableIntStateOf(0) }
    var finished by remember(round) { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }

    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (finished) {
                QuizResultView(
                    correct = correct,
                    total = questions.size,
                    onPlayAgain = { round += 1 },
                    onBack = onBack,
                    footer = footer
                )
            } else {
                val question = questions[index.coerceIn(0, questions.size - 1)]

                fun mark() {
                    if (judged != null || typed.isBlank()) return
                    val ok = AnswerCheck.isCorrect(typed, question.answer, question.alternatives)
                    judged = ok
                    if (ok) correct += 1
                    keyboard?.hide()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.quiz_progress, index + 1, questions.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (index.toFloat() / questions.size).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(Modifier.height(22.dp))

                    val verdict = judged
                    val cardColors = when (verdict) {
                        true -> listOf(Brand.Mint, Brand.Sky)
                        false -> listOf(Brand.Coral, Brand.Sun)
                        else -> listOf(Brand.Violet, Brand.Bubblegum)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(cardColors))
                            .padding(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (verdict) {
                                    true -> "✅"
                                    false -> "📘"
                                    else -> "❓"
                                },
                                fontSize = 30.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = question.prompt,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 29.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = typed,
                        onValueChange = { if (judged == null) typed = it },
                        label = { Text(stringResource(R.string.type_your_answer)) },
                        enabled = judged == null,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { mark() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    if (verdict == null) {
                        Button(
                            onClick = { mark() },
                            enabled = typed.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.check_answer))
                        }
                    } else {
                        val tint by animateColorAsState(
                            targetValue = if (verdict) Brand.Mint else Brand.Coral,
                            label = "verdict"
                        )
                        Text(
                            text = stringResource(
                                if (verdict) R.string.answer_correct else R.string.answer_wrong
                            ),
                            color = tint,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!verdict) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.correct_answer_was, question.answer),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (index + 1 >= questions.size) {
                                    onFinish(correct, questions.size)
                                    if (correct == questions.size) confetti += 1
                                    finished = true
                                } else {
                                    index += 1
                                    typed = ""
                                    judged = null
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                stringResource(
                                    if (index + 1 >= questions.size) R.string.see_result
                                    else R.string.next_question
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }

            ConfettiBurst(trigger = confetti)
        }
    }
}

@Composable
private fun QuizResultView(
    correct: Int,
    total: Int,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
    footer: (@Composable () -> Unit)?
) {
    val perfect = correct == total
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (perfect) "🏆" else "👏", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.quiz_done),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.quiz_score, correct, total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (footer != null) {
                Spacer(Modifier.height(14.dp))
                footer()
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPlayAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.play_again))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.back_to_book))
            }
        }
        if (perfect) {
            ConfettiBurst(trigger = total + 1)
        }
    }
}
