package com.poodicraft.bookquest.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Which side of the classroom an account sits on. */
enum class UserRole(val id: String) {
    UNKNOWN("unknown"),
    STUDENT("student"),
    TEACHER("teacher");

    companion object {
        fun fromId(id: String?): UserRole = values().firstOrNull { it.id == id } ?: UNKNOWN
    }
}

data class SchoolProfile(
    val role: UserRole = UserRole.UNKNOWN,
    val displayName: String = "",
    val school: String = "",
    val subject: String = "",
    val classIds: List<String> = emptyList()
)

data class SchoolClass(
    val id: String = "",
    val name: String = "",
    val school: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val joinCode: String = "",
    val createdAt: Long = 0L
)

data class ClassMember(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val joinedAt: Long = 0L
)

data class QuizQuestion(
    val prompt: String = "",
    val answer: String = "",
    val alternatives: List<String> = emptyList()
)

/** A book or a quiz a teacher has handed to a class. */
data class Assignment(
    val id: String = "",
    val kind: String = KIND_BOOK,
    val title: String = "",
    val author: String = "",
    val subjectId: String = Subject.GENERAL.id,
    val note: String = "",
    /** Inline text for small text books, so students get the whole thing at once. */
    val content: String = "",
    /** Base64 of a small binary book — a PDF, an EPUB — sent whole. */
    val contentBase64: String = "",
    /** Which format [contentBase64] holds, as a [BookFormat] id. */
    val contentFormat: String = "",
    val questions: List<QuizQuestion> = emptyList(),
    val dueAt: Long = 0L,
    val createdAt: Long = 0L
) {
    val isQuiz: Boolean get() = kind == KIND_QUIZ
    val isHomework: Boolean get() = kind == KIND_HOMEWORK

    companion object {
        const val KIND_BOOK = "book"
        const val KIND_QUIZ = "quiz"
        const val KIND_HOMEWORK = "homework"

        /** Firestore documents cap at 1 MiB, so inline book text is kept well under it. */
        const val MAX_INLINE_CHARS = 400_000

        /**
         * Base64 grows a file by a third, so this is the largest binary book
         * that still leaves room inside a single Firestore document.
         */
        const val MAX_INLINE_BYTES = 550_000
    }
}

/** A message on a class stream, optionally carrying a file. */
data class ClassPost(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorRole: String = "",
    val text: String = "",
    val fileName: String = "",
    val fileFormat: String = "",
    val fileBase64: String = "",
    val createdAt: Long = 0L
)

/** One student's answer to a piece of homework, and the teacher's marking. */
data class Submission(
    val studentUid: String = "",
    val studentName: String = "",
    val text: String = "",
    val fileName: String = "",
    val fileFormat: String = "",
    val fileBase64: String = "",
    val submittedAt: Long = 0L,
    val grade: String = "",
    val feedback: String = "",
    val gradedAt: Long = 0L
) {
    val isGraded: Boolean get() = gradedAt > 0L
}

data class QuizResult(
    val studentUid: String = "",
    val studentName: String = "",
    val assignmentId: String = "",
    val assignmentTitle: String = "",
    val correct: Int = 0,
    val total: Int = 0,
    val at: Long = 0L
)

/**
 * Everything shared between a teacher and their students: profiles, classes,
 * rosters, assignments and quiz results.
 *
 * Reads and writes go straight to Firestore rather than through a local cache,
 * because a classroom is inherently shared state and staleness would show.
 */
class Classroom private constructor(private val appContext: Context) {

    private val cache = appContext
        .getSharedPreferences("bookquest_classroom", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(readCachedProfile())
    val profile: StateFlow<SchoolProfile> = _profile.asStateFlow()

    /**
     * The classes you belong to. Held here rather than in a screen so that
     * walking off to the library and back cannot make a class disappear.
     */
    private val _classes = MutableStateFlow(readCachedClasses())
    val classes: StateFlow<List<SchoolClass>> = _classes.asStateFlow()

    private var loadedOnce = false

    private val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            null
        }

    private val db: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            null
        }

    val uid: String? get() = auth?.currentUser?.uid

    // ---------------------------------------------------------------- profile

    /**
     * Refreshes the profile and class list, once per sign in unless [force]d.
     *
     * A failure here leaves whatever was already known in place. Wiping the
     * cached profile on a dropped request is what used to throw people back to
     * the role picker and empty their class list mid session.
     */
    suspend fun ensureLoaded(force: Boolean = false) {
        if (loadedOnce && !force) return
        loadedOnce = true
        loadProfile()
        refreshClasses()
    }

    suspend fun loadProfile(): SchoolProfile = withContext(Dispatchers.IO) {
        val id = uid
        val store = db
        if (id == null || store == null) return@withContext _profile.value
        try {
            val snapshot = store.collection("users").document(id).get().await()
            if (!snapshot.exists()) return@withContext _profile.value
            val ids = snapshot.get("classIds") as? List<*>
            val remoteRole = UserRole.fromId(snapshot.getString("role"))
            val current = _profile.value
            val loaded = SchoolProfile(
                // A blank remote role means the document predates the role, so
                // keep whatever this device already knows rather than resetting.
                role = if (remoteRole == UserRole.UNKNOWN) current.role else remoteRole,
                displayName = snapshot.getString("name")
                    ?: current.displayName.ifBlank { auth?.currentUser?.displayName.orEmpty() },
                school = snapshot.getString("school") ?: current.school,
                subject = snapshot.getString("subject") ?: current.subject,
                classIds = (ids?.mapNotNull { it as? String } ?: emptyList())
                    .plus(current.classIds)
                    .distinct()
            )
            _profile.value = loaded
            writeCachedProfile(loaded)
            loaded
        } catch (e: Exception) {
            _profile.value
        }
    }

    /** Reloads the class list, keeping the cached one if the request fails. */
    suspend fun refreshClasses(): List<SchoolClass> {
        val result = myClasses()
        val loaded = result.getOrNull()
        if (loaded != null) {
            _classes.value = loaded
            writeCachedClasses(loaded)
        }
        return _classes.value
    }

    /** Forgets everything about this account, for sign out. */
    fun clear() {
        loadedOnce = false
        _profile.value = SchoolProfile()
        _classes.value = emptyList()
        cache.edit().clear().apply()
    }

    suspend fun saveProfile(
        role: UserRole,
        displayName: String,
        school: String,
        subject: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id = uid ?: return@withContext Result.failure(NotSignedIn())
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            store.collection("users").document(id).set(
                mapOf(
                    "role" to role.id,
                    "name" to displayName.trim(),
                    "school" to school.trim(),
                    "subject" to subject.trim()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            val updated = _profile.value.copy(
                role = role,
                displayName = displayName.trim(),
                school = school.trim(),
                subject = subject.trim()
            )
            _profile.value = updated
            writeCachedProfile(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------- classes

    suspend fun myClasses(): Result<List<SchoolClass>> = withContext(Dispatchers.IO) {
        val id = uid ?: return@withContext Result.failure(NotSignedIn())
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            // Ask both ways and take the union. Keying this off the cached role
            // meant a momentarily unknown role hid a teacher's own classes.
            val taught = try {
                store.collection("classes")
                    .whereEqualTo("teacherUid", id)
                    .get().await()
                    .documents.mapNotNull { classFrom(it.id, it.data) }
            } catch (e: Exception) {
                emptyList()
            }

            val joined = _profile.value.classIds.mapNotNull { classId ->
                try {
                    val doc = store.collection("classes").document(classId).get().await()
                    classFrom(doc.id, doc.data)
                } catch (e: Exception) {
                    null
                }
            }

            val list = (taught + joined).distinctBy { it.id }
            Result.success(list.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createClass(name: String, school: String): Result<SchoolClass> =
        withContext(Dispatchers.IO) {
            val id = uid ?: return@withContext Result.failure(NotSignedIn())
            val store = db ?: return@withContext Result.failure(NotSignedIn())
            if (name.isBlank()) return@withContext Result.failure(EmptyName())
            try {
                val code = freeJoinCode(store)
                val document = store.collection("classes").document()
                val teacherName = _profile.value.displayName
                    .ifBlank { auth?.currentUser?.displayName.orEmpty() }
                val created = SchoolClass(
                    id = document.id,
                    name = name.trim(),
                    school = school.trim(),
                    teacherUid = id,
                    teacherName = teacherName,
                    joinCode = code,
                    createdAt = System.currentTimeMillis()
                )
                document.set(
                    mapOf(
                        "name" to created.name,
                        "school" to created.school,
                        "teacherUid" to created.teacherUid,
                        "teacherName" to created.teacherName,
                        "joinCode" to created.joinCode,
                        "createdAt" to created.createdAt
                    )
                ).await()
                _classes.value = listOf(created) + _classes.value
                writeCachedClasses(_classes.value)
                Result.success(created)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Finds a class by its code and adds the signed in student to the roster. */
    suspend fun joinClass(code: String): Result<SchoolClass> = withContext(Dispatchers.IO) {
        val id = uid ?: return@withContext Result.failure(NotSignedIn())
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        val cleaned = code.trim().uppercase()
        if (cleaned.length < 4) return@withContext Result.failure(BadCode())
        try {
            val found = store.collection("classes")
                .whereEqualTo("joinCode", cleaned)
                .limit(1)
                .get().await()
            val document = found.documents.firstOrNull()
                ?: return@withContext Result.failure(BadCode())
            val target = classFrom(document.id, document.data)
                ?: return@withContext Result.failure(BadCode())

            val user = auth?.currentUser
            val name = _profile.value.displayName
                .ifBlank { user?.displayName.orEmpty() }
                .ifBlank { user?.email.orEmpty() }

            store.collection("classes").document(target.id)
                .collection("members").document(id)
                .set(
                    mapOf(
                        "name" to name,
                        "email" to user?.email.orEmpty(),
                        "joinedAt" to System.currentTimeMillis()
                    )
                ).await()

            store.collection("users").document(id).set(
                mapOf("classIds" to FieldValue.arrayUnion(target.id)),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            val updated = _profile.value.copy(
                classIds = (_profile.value.classIds + target.id).distinct()
            )
            _profile.value = updated
            writeCachedProfile(updated)
            if (_classes.value.none { it.id == target.id }) {
                _classes.value = _classes.value + target
                writeCachedClasses(_classes.value)
            }
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun members(classId: String): Result<List<ClassMember>> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            val list = store.collection("classes").document(classId)
                .collection("members").get().await()
                .documents.map { doc ->
                    ClassMember(
                        uid = doc.id,
                        name = doc.getString("name").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        joinedAt = doc.getLong("joinedAt") ?: 0L
                    )
                }
            Result.success(list.sortedBy { it.name.lowercase() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------ assignments

    suspend fun assignments(classId: String): Result<List<Assignment>> =
        withContext(Dispatchers.IO) {
            val store = db ?: return@withContext Result.failure(NotSignedIn())
            try {
                val list = store.collection("classes").document(classId)
                    .collection("assignments").get().await()
                    .documents.map { doc -> assignmentFrom(doc.id, doc) }
                Result.success(list.sortedByDescending { it.createdAt })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun assignBook(
        classId: String,
        title: String,
        author: String,
        subject: Subject,
        note: String,
        content: String,
        contentBase64: String = "",
        contentFormat: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        if (title.isBlank()) return@withContext Result.failure(EmptyName())
        try {
            store.collection("classes").document(classId)
                .collection("assignments").document()
                .set(
                    mapOf(
                        "kind" to Assignment.KIND_BOOK,
                        "title" to title.trim(),
                        "author" to author.trim(),
                        "subject" to subject.id,
                        "note" to note.trim(),
                        "content" to content.take(Assignment.MAX_INLINE_CHARS),
                        "contentBase64" to contentBase64,
                        "contentFormat" to contentFormat,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignQuiz(
        classId: String,
        title: String,
        questions: List<QuizQuestion>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        if (title.isBlank() || questions.isEmpty()) return@withContext Result.failure(EmptyName())
        try {
            store.collection("classes").document(classId)
                .collection("assignments").document()
                .set(
                    mapOf(
                        "kind" to Assignment.KIND_QUIZ,
                        "title" to title.trim(),
                        "createdAt" to System.currentTimeMillis(),
                        "questions" to questions.map { question ->
                            mapOf(
                                "prompt" to question.prompt.trim(),
                                "answer" to question.answer.trim(),
                                "alternatives" to question.alternatives.map { it.trim() }
                            )
                        }
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAssignment(classId: String, assignmentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val store = db ?: return@withContext Result.failure(NotSignedIn())
            try {
                store.collection("classes").document(classId)
                    .collection("assignments").document(assignmentId)
                    .delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ------------------------------------------------------------- the stream

    suspend fun posts(classId: String): Result<List<ClassPost>> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            val list = store.collection("classes").document(classId)
                .collection("posts").get().await()
                .documents.map { doc ->
                    ClassPost(
                        id = doc.id,
                        authorUid = doc.getString("authorUid").orEmpty(),
                        authorName = doc.getString("authorName").orEmpty(),
                        authorRole = doc.getString("authorRole").orEmpty(),
                        text = doc.getString("text").orEmpty(),
                        fileName = doc.getString("fileName").orEmpty(),
                        fileFormat = doc.getString("fileFormat").orEmpty(),
                        fileBase64 = doc.getString("fileBase64").orEmpty(),
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }
            Result.success(list.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPost(
        classId: String,
        text: String,
        fileName: String = "",
        fileFormat: String = "",
        fileBase64: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id = uid ?: return@withContext Result.failure(NotSignedIn())
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        if (text.isBlank() && fileBase64.isBlank()) return@withContext Result.failure(EmptyName())
        try {
            store.collection("classes").document(classId)
                .collection("posts").document()
                .set(
                    mapOf(
                        "authorUid" to id,
                        "authorName" to currentName(),
                        "authorRole" to _profile.value.role.id,
                        "text" to text.trim(),
                        "fileName" to fileName,
                        "fileFormat" to fileFormat,
                        "fileBase64" to fileBase64,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(classId: String, postId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val store = db ?: return@withContext Result.failure(NotSignedIn())
            try {
                store.collection("classes").document(classId)
                    .collection("posts").document(postId).delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---------------------------------------------------------------- homework

    suspend fun assignHomework(
        classId: String,
        title: String,
        instructions: String,
        dueAt: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        if (title.isBlank()) return@withContext Result.failure(EmptyName())
        try {
            store.collection("classes").document(classId)
                .collection("assignments").document()
                .set(
                    mapOf(
                        "kind" to Assignment.KIND_HOMEWORK,
                        "title" to title.trim(),
                        "note" to instructions.trim(),
                        "dueAt" to dueAt,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitHomework(
        classId: String,
        assignmentId: String,
        text: String,
        fileName: String = "",
        fileFormat: String = "",
        fileBase64: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id = uid ?: return@withContext Result.failure(NotSignedIn())
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            store.collection("classes").document(classId)
                .collection("assignments").document(assignmentId)
                .collection("submissions").document(id)
                .set(
                    mapOf(
                        "studentUid" to id,
                        "studentName" to currentName(),
                        "text" to text.trim(),
                        "fileName" to fileName,
                        "fileFormat" to fileFormat,
                        "fileBase64" to fileBase64,
                        "submittedAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submissions(classId: String, assignmentId: String): Result<List<Submission>> =
        withContext(Dispatchers.IO) {
            val store = db ?: return@withContext Result.failure(NotSignedIn())
            try {
                val list = store.collection("classes").document(classId)
                    .collection("assignments").document(assignmentId)
                    .collection("submissions").get().await()
                    .documents.map { submissionFrom(it) }
                Result.success(list.sortedBy { it.studentName.lowercase() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun mySubmission(classId: String, assignmentId: String): Submission? =
        withContext(Dispatchers.IO) {
            val id = uid ?: return@withContext null
            val store = db ?: return@withContext null
            try {
                val doc = store.collection("classes").document(classId)
                    .collection("assignments").document(assignmentId)
                    .collection("submissions").document(id).get().await()
                if (doc.exists()) submissionFrom(doc) else null
            } catch (e: Exception) {
                null
            }
        }

    suspend fun gradeSubmission(
        classId: String,
        assignmentId: String,
        studentUid: String,
        grade: String,
        feedback: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            store.collection("classes").document(classId)
                .collection("assignments").document(assignmentId)
                .collection("submissions").document(studentUid)
                .set(
                    mapOf(
                        "grade" to grade.trim(),
                        "feedback" to feedback.trim(),
                        "gradedAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun submissionFrom(
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): Submission = Submission(
        studentUid = doc.getString("studentUid") ?: doc.id,
        studentName = doc.getString("studentName").orEmpty(),
        text = doc.getString("text").orEmpty(),
        fileName = doc.getString("fileName").orEmpty(),
        fileFormat = doc.getString("fileFormat").orEmpty(),
        fileBase64 = doc.getString("fileBase64").orEmpty(),
        submittedAt = doc.getLong("submittedAt") ?: 0L,
        grade = doc.getString("grade").orEmpty(),
        feedback = doc.getString("feedback").orEmpty(),
        gradedAt = doc.getLong("gradedAt") ?: 0L
    )

    private fun currentName(): String = _profile.value.displayName
        .ifBlank { auth?.currentUser?.displayName.orEmpty() }
        .ifBlank { auth?.currentUser?.email.orEmpty() }

    // ----------------------------------------------------------------- results

    suspend fun submitResult(
        classId: String,
        assignment: Assignment,
        correct: Int,
        total: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id = uid ?: return@withContext Result.failure(NotSignedIn())
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            val name = _profile.value.displayName
                .ifBlank { auth?.currentUser?.displayName.orEmpty() }
                .ifBlank { auth?.currentUser?.email.orEmpty() }
            store.collection("classes").document(classId)
                .collection("results").document(id + "_" + assignment.id)
                .set(
                    mapOf(
                        "studentUid" to id,
                        "studentName" to name,
                        "assignmentId" to assignment.id,
                        "assignmentTitle" to assignment.title,
                        "correct" to correct,
                        "total" to total,
                        "at" to System.currentTimeMillis()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun results(classId: String): Result<List<QuizResult>> = withContext(Dispatchers.IO) {
        val store = db ?: return@withContext Result.failure(NotSignedIn())
        try {
            val list = store.collection("classes").document(classId)
                .collection("results").get().await()
                .documents.map { doc ->
                    QuizResult(
                        studentUid = doc.getString("studentUid").orEmpty(),
                        studentName = doc.getString("studentName").orEmpty(),
                        assignmentId = doc.getString("assignmentId").orEmpty(),
                        assignmentTitle = doc.getString("assignmentTitle").orEmpty(),
                        correct = (doc.getLong("correct") ?: 0L).toInt(),
                        total = (doc.getLong("total") ?: 0L).toInt(),
                        at = doc.getLong("at") ?: 0L
                    )
                }
            Result.success(list.sortedByDescending { it.at })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------------------- plumbing

    private fun classFrom(id: String, data: Map<String, Any>?): SchoolClass? {
        if (data == null) return null
        return SchoolClass(
            id = id,
            name = data["name"] as? String ?: return null,
            school = data["school"] as? String ?: "",
            teacherUid = data["teacherUid"] as? String ?: "",
            teacherName = data["teacherName"] as? String ?: "",
            joinCode = data["joinCode"] as? String ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun assignmentFrom(
        id: String,
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): Assignment {
        val rawQuestions = doc.get("questions") as? List<*>
        val questions = rawQuestions?.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val prompt = map["prompt"] as? String ?: return@mapNotNull null
            QuizQuestion(
                prompt = prompt,
                answer = map["answer"] as? String ?: "",
                alternatives = (map["alternatives"] as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList()
            )
        } ?: emptyList()
        return Assignment(
            id = id,
            kind = doc.getString("kind") ?: Assignment.KIND_BOOK,
            title = doc.getString("title").orEmpty(),
            author = doc.getString("author").orEmpty(),
            subjectId = doc.getString("subject") ?: Subject.GENERAL.id,
            note = doc.getString("note").orEmpty(),
            dueAt = doc.getLong("dueAt") ?: 0L,
            content = doc.getString("content").orEmpty(),
            contentBase64 = doc.getString("contentBase64").orEmpty(),
            contentFormat = doc.getString("contentFormat").orEmpty(),
            questions = questions,
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    }

    /** Six readable characters, checked against Firestore so no two classes collide. */
    private suspend fun freeJoinCode(store: FirebaseFirestore): String {
        repeat(8) {
            val candidate = randomCode()
            val existing = store.collection("classes")
                .whereEqualTo("joinCode", candidate)
                .limit(1)
                .get().await()
            if (existing.isEmpty) return candidate
        }
        return randomCode()
    }

    private fun randomCode(): String {
        // No O/0 or I/1: these get read off a whiteboard.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    // ------------------------------------------------------------ local cache

    private fun readCachedProfile(): SchoolProfile = SchoolProfile(
        role = UserRole.fromId(cache.getString("role", null)),
        displayName = cache.getString("name", "").orEmpty(),
        school = cache.getString("school", "").orEmpty(),
        subject = cache.getString("subject", "").orEmpty(),
        classIds = cache.getString("classIds", "").orEmpty()
            .split(",").filter { it.isNotBlank() }
    )

    private fun writeCachedProfile(value: SchoolProfile) {
        cache.edit()
            .putString("role", value.role.id)
            .putString("name", value.displayName)
            .putString("school", value.school)
            .putString("subject", value.subject)
            .putString("classIds", value.classIds.joinToString(","))
            .apply()
    }

    private fun readCachedClasses(): List<SchoolClass> = try {
        val array = JSONArray(cache.getString("classes", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            SchoolClass(
                id = item.optString("id"),
                name = item.optString("name"),
                school = item.optString("school"),
                teacherUid = item.optString("teacherUid"),
                teacherName = item.optString("teacherName"),
                joinCode = item.optString("joinCode"),
                createdAt = item.optLong("createdAt")
            )
        }.filter { it.id.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeCachedClasses(value: List<SchoolClass>) {
        try {
            val array = JSONArray()
            value.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("name", item.name)
                        .put("school", item.school)
                        .put("teacherUid", item.teacherUid)
                        .put("teacherName", item.teacherName)
                        .put("joinCode", item.joinCode)
                        .put("createdAt", item.createdAt)
                )
            }
            cache.edit().putString("classes", array.toString()).apply()
        } catch (e: Exception) {
            // The in memory list is still correct; only the offline copy is lost.
        }
    }

    class NotSignedIn : Exception("No account is signed in")
    class BadCode : Exception("No class has that code")
    class EmptyName : Exception("A name is required")

    companion object {
        @Volatile
        private var instance: Classroom? = null

        fun get(context: Context): Classroom = instance ?: synchronized(this) {
            instance ?: Classroom(context.applicationContext).also { instance = it }
        }
    }
}
