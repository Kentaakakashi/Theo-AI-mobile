package com.example.data.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class AccountProfile(
    val uid: String,
    val username: String?,
    val displayName: String,
    val email: String?
)

class UsernameAlreadyTakenException : IllegalStateException("That username is already in use.")

class FirebaseAccountRepository private constructor(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChangedBy { it?.uid }

    suspend fun signInWithGoogle(activity: Activity): FirebaseUser {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        check(resourceId != 0) {
            "Google sign-in configuration is missing. Add the updated google-services.json after enabling Google sign-in in Firebase."
        }

        val webClientId = context.getString(resourceId)
        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request
        )
        val credential = result.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Google did not return a supported sign-in credential."
        }

        val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
        val signedInUser = auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase completed sign-in but did not return a user.")

        ensureUserProfile(signedInUser)
        return signedInUser
    }

    suspend fun ensureUserProfile(user: FirebaseUser): AccountProfile {
        val profileRef = firestore.collection("users").document(user.uid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(profileRef)
            if (!snapshot.exists()) {
                transaction.set(
                    profileRef,
                    mapOf(
                        "uid" to user.uid,
                        "username" to null,
                        "displayName" to (user.displayName ?: ""),
                        "email" to user.email,
                        "photoUrl" to user.photoUrl?.toString(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastSeenAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                transaction.update(profileRef, "lastSeenAt", FieldValue.serverTimestamp())
            }
            true
        }.await()

        return getProfile(user.uid)
            ?: error("Theo could not read the account profile after saving it.")
    }

    suspend fun getProfile(uid: String): AccountProfile? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        return AccountProfile(
            uid = snapshot.getString("uid") ?: uid,
            username = snapshot.getString("username")?.takeIf { it.isNotBlank() },
            displayName = snapshot.getString("displayName").orEmpty(),
            email = snapshot.getString("email")
        )
    }

    suspend fun completeProfile(
        uid: String,
        usernameInput: String,
        displayNameInput: String
    ): AccountProfile {
        val username = UsernamePolicy.normalize(usernameInput)
        UsernamePolicy.validationError(username)?.let { throw IllegalArgumentException(it) }
        val displayName = displayNameInput.trim()
        require(displayName.length in 2..40) {
            "Display name must contain between 2 and 40 characters."
        }

        val userRef = firestore.collection("users").document(uid)
        val targetUsernameRef = firestore.collection("usernames").document(username)

        firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            check(userSnapshot.exists()) { "Your Theo profile could not be found. Sign in again." }

            val targetSnapshot = transaction.get(targetUsernameRef)
            val oldUsername = userSnapshot.getString("username")
                ?.takeIf { it.isNotBlank() && it != username }
            val oldUsernameRef = oldUsername?.let {
                firestore.collection("usernames").document(it)
            }
            val oldUsernameSnapshot = oldUsernameRef?.let { transaction.get(it) }

            if (targetSnapshot.exists() && targetSnapshot.getString("uid") != uid) {
                throw UsernameAlreadyTakenException()
            }

            if (!targetSnapshot.exists()) {
                transaction.set(
                    targetUsernameRef,
                    mapOf(
                        "uid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (oldUsernameRef != null && oldUsernameSnapshot?.getString("uid") == uid) {
                transaction.delete(oldUsernameRef)
            }

            transaction.update(
                userRef,
                mapOf(
                    "username" to username,
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            true
        }.await()

        return getProfile(uid)
            ?: error("Theo could not read the updated account profile.")
    }

    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    companion object {
        fun createIfConfigured(context: Context): FirebaseAccountRepository? {
            val appContext = context.applicationContext
            val firebaseApp = runCatching {
                FirebaseApp.getApps(appContext).firstOrNull()
                    ?: FirebaseApp.initializeApp(appContext)
            }.getOrNull() ?: return null

            return runCatching {
                FirebaseAccountRepository(
                    context = appContext,
                    auth = FirebaseAuth.getInstance(firebaseApp),
                    firestore = FirebaseFirestore.getInstance(firebaseApp)
                )
            }.getOrNull()
        }
    }
}

object UsernamePolicy {
    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun validationError(value: String): String? {
        return if (Regex("[a-z0-9_]{3,20}").matches(value)) {
            null
        } else {
            "Username must be 3–20 characters and use only letters, numbers, or underscores."
        }
    }
}
