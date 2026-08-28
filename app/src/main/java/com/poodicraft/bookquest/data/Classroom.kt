package com.poodicraft.bookquest.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
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
    val questions: List<QuizQuestion> = emptyList(),
    val createdAt: Long = 0L
) {
    val isQuiz: Boolean get() = kind == KIND_QUIZ

    companion object {
        const val KIND_BOOK = "book"
        const val KIND_QUIZ = "quiz"

        /** Firestore documents cap at 1 MiB, so inline book text is kept well under it. */
        const val MAX_INLINE_CHARS = 400_000
    }
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
class Classroom private constructor() {

    private val _profile = MutableStateFlow(SchoolProfile())
    val profile: StateFlow<SchoolProfile> = _profile.asStateFlow()

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

    suspend fun loadProfile(): SchoolProfile = withContext(Dispatchers.IO) {
        val id = uid
        val store = db
        if (id == null || store == null) {
            _profile.value = SchoolProfile()
            return@withContext _profile.value
        }
        try {
            val snapshot = store.collection("users").document(id).get().await()
            val ids = snapshot.get("classIds") as? List<*>
            val loaded = SchoolProfile(
                role = UserRole.fromId(snapshot.getString("role")),
                displayName = snapshot.getString("name")
                    ?: auth?.currentUser?.displayName.orEmpty(),
                school = snapshot.getString("school").orEmpty(),
                subject = snapshot.getString("subject").orEmpty(),
                classIds = ids?.mapNotNull { it as? String } ?: emptyList()
            )
            _profile.value = loaded
            loaded
        } catch (e: Exception) {
            _profile.value = SchoolProfile()
            _profile.value
        }
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
            _profile.value = _profile.value.copy(
                role = role,
                displayName = displayName.trim(),
                school = school.trim(),
                subject = subject.trim()
            )
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
            val list = if (_profile.value.role == UserRole.TEACHER) {
                store.collection("classes")
                    .whereEqualTo("teacherUid", id)
                    .get().await()
                    .documents.mapNotNull { classFrom(it.id, it.data) }
            } else {
                val ids = _profile.value.classIds
                ids.mapNotNull { classId ->
                    val doc = store.collection("classes").document(classId).get().await()
                    classFrom(doc.id, doc.data)
                }
            }
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

            _profile.value = _profile.value.copy(
                classIds = (_profile.value.classIds + target.id).distinct()
            )
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
        content: String
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

    // ---------------------------------------------------------------- results

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
            content = doc.getString("content").orEmpty(),
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

    class NotSignedIn : Exception("No account is signed in")
    class BadCode : Exception("No class has that code")
    class EmptyName : Exception("A name is required")

    companion object {
        @Volatile
        private var instance: Classroom? = null

        fun get(): Classroom = instance ?: synchronized(this) {
            instance ?: Classroom().also { instance = it }
        }
    }
}
