package com.poodicraft.bookquest.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Who, if anyone, is signed in. */
sealed class AccountState {
    /** No google-services.json was bundled, so the feature is switched off. */
    data object NotConfigured : AccountState()
    data object SignedOut : AccountState()
    data class SignedIn(
        val name: String,
        val email: String,
        val provider: String
    ) : AccountState()
}

/** What the last backup attempt did. */
sealed class SyncState {
    data object Idle : SyncState()
    data object Working : SyncState()
    data class Done(val at: Long) : SyncState()
    data class Failed(val reason: String) : SyncState()
}

/**
 * Google sign in through Credential Manager, plus a merge based backup of the
 * reading progress into Cloud Firestore.
 *
 * The whole class is inert unless the app was built with a google-services.json:
 * every entry point reports [AccountState.NotConfigured] instead of crashing, so
 * an unconfigured build still runs perfectly well offline.
 */
class CloudSync private constructor(
    private val appContext: Context,
    private val repository: LibraryRepository
) {

    private val _account = MutableStateFlow<AccountState>(AccountState.SignedOut)
    val account: StateFlow<AccountState> = _account.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    /**
     * The OAuth web client id. The google-services plugin generates this string
     * resource from google-services.json, so looking it up by name means the app
     * still compiles when the file is absent.
     */
    private val webClientId: String? by lazy {
        val id = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        if (id != 0) appContext.getString(id) else null
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    val isConfigured: Boolean get() = webClientId != null && auth != null

    init {
        refresh()
    }

    /** Re-reads the current Firebase user into [account]. */
    fun refresh() {
        if (!isConfigured) {
            _account.value = AccountState.NotConfigured
            return
        }
        val user = auth?.currentUser
        _account.value = if (user == null) {
            AccountState.SignedOut
        } else {
            val provider = user.providerData
                .map { it.providerId }
                .firstOrNull { it != "firebase" }
                .orEmpty()
            AccountState.SignedIn(
                name = user.displayName ?: user.email.orEmpty(),
                email = user.email.orEmpty(),
                provider = provider
            )
        }
    }

    /**
     * Shows the Google account chooser and signs the chosen account in to Firebase,
     * then immediately runs a first sync so existing progress is pulled down.
     */
    suspend fun signIn(activity: Activity): Result<Unit> {
        val clientId = webClientId ?: return Result.failure(CloudNotConfigured())
        val firebaseAuth = auth ?: return Result.failure(CloudNotConfigured())

        // The first pass only offers accounts that already use this app, which is
        // the quiet one-tap path. If there are none, ask again with every account.
        val token = requestIdToken(activity, clientId, filterAuthorized = true)
            ?: requestIdToken(activity, clientId, filterAuthorized = false)
            ?: return Result.failure(lastCredentialError ?: SignInCancelled())

        return try {
            val credential = GoogleAuthProvider.getCredential(token, null)
            firebaseAuth.signInWithCredential(credential).await()
            refresh()
            syncNow()
        } catch (e: Exception) {
            _sync.value = SyncState.Failed(e.message.orEmpty())
            Result.failure(e)
        }
    }

    private var lastCredentialError: Exception? = null

    private suspend fun requestIdToken(
        activity: Activity,
        clientId: String,
        filterAuthorized: Boolean
    ): String? {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(filterAuthorized)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        } catch (e: Exception) {
            lastCredentialError = e
            null
        }
    }

    /** Signs in with an email address and password. */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(CloudNotConfigured())
        return try {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            refresh()
            syncNow()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Creates a new account from an email address and password, then signs in. */
    suspend fun createAccountWithEmail(email: String, password: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(CloudNotConfigured())
        return try {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            refresh()
            syncNow()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * True when this account signs in with a password at all. Accounts that
     * arrived through Google have no password to change, so the setting is
     * hidden rather than offered and then refused.
     */
    val hasPassword: Boolean
        get() = auth?.currentUser?.providerData
            ?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    /**
     * Changes the account password. Firebase only allows this on a recent sign
     * in, so the current password is asked for and used to re-authenticate
     * first: that both satisfies Firebase and stops someone changing the
     * password on a phone that was left unlocked.
     */
    suspend fun changePassword(current: String, replacement: String): Result<Unit> {
        val user = auth?.currentUser ?: return Result.failure(NotSignedIn())
        val email = user.email
        if (email.isNullOrBlank() || !hasPassword) return Result.failure(NoPassword())
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()
            user.updatePassword(replacement).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Puts the name from the profile editor onto the Firebase account itself. */
    suspend fun updateDisplayName(name: String): Result<Unit> {
        val user = auth?.currentUser ?: return Result.failure(NotSignedIn())
        return try {
            user.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
            ).await()
            refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Emails a password reset link. */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(CloudNotConfigured())
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Erases the account: the stored reading history and profile first, then the
     * Firebase user itself.
     *
     * Data goes before the user because deleting the user first would revoke the
     * very credentials the rules check, stranding the documents where nobody can
     * ever reach or remove them. Classes a teacher owns are deleted separately,
     * by the caller, for the same reason — while they still have permission.
     *
     * Firebase only allows deletion on a recent sign in. A stale session comes
     * back as a plain failure the caller reports, and signing in again fixes it.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val user = auth?.currentUser ?: return Result.failure(NotSignedIn())
        val db = firestore ?: return Result.failure(CloudNotConfigured())
        val uid = user.uid
        return try {
            withContext(Dispatchers.IO) {
                try {
                    db.collection("users").document(uid).delete().await()
                } catch (e: Exception) {
                    // Losing the stored copy is not a reason to keep the account
                    // alive; the user asked for it gone.
                }
                user.delete().await()
            }
            try {
                Classroom.get(appContext).clear()
            } catch (e: Exception) {
                // The sign out below still leaves the app in a clean state.
            }
            _sync.value = SyncState.Idle
            refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            // Nothing else to do; the state refresh below still runs.
        }
        try {
            Classroom.get(appContext).clear()
        } catch (e: Exception) {
            // Signing out still succeeds even if the cache will not clear.
        }
        _sync.value = SyncState.Idle
        refresh()
    }

    /**
     * Pulls the stored snapshot, merges it into local state, and writes the merged
     * result straight back. Running it twice in a row is harmless.
     */
    suspend fun syncNow(): Result<Unit> {
        if (!isConfigured) return Result.failure(CloudNotConfigured())
        val uid = auth?.currentUser?.uid ?: return Result.failure(NotSignedIn())
        val db = firestore ?: return Result.failure(CloudNotConfigured())

        _sync.value = SyncState.Working
        return try {
            withContext(Dispatchers.IO) {
                val document = db.collection("users").document(uid)
                val stored = document.get().await()
                val payload = stored.getString("payload")
                if (!payload.isNullOrBlank()) {
                    repository.mergeSnapshot(payload)
                }
                val merged = repository.exportSnapshot()
                // Merge, never replace: the same document also holds the role,
                // name, school and class ids, and a plain set() wipes them.
                document.set(
                    mapOf(
                        "payload" to merged,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            }
            _sync.value = SyncState.Done(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            _sync.value = SyncState.Failed(e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /** Best effort background push, used when the app goes to the background. */
    suspend fun pushQuietly() {
        if (!isConfigured || auth?.currentUser == null) return
        syncNow()
    }

    class CloudNotConfigured : Exception("Google sign in is not configured in this build")
    class NotSignedIn : Exception("No account is signed in")
    class SignInCancelled : Exception("Sign in was cancelled")
    class NoPassword : Exception("This account does not sign in with a password")

    companion object {
        @Volatile
        private var instance: CloudSync? = null

        fun get(context: Context): CloudSync {
            return instance ?: synchronized(this) {
                instance ?: CloudSync(
                    context.applicationContext,
                    LibraryRepository.get(context)
                ).also { instance = it }
            }
        }
    }
}
