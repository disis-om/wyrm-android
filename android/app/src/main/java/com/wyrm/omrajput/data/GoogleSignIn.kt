package com.wyrm.omrajput.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// Google sign-in (no screen uses it today), kept apart from WyrmRepository so
// the repository itself also builds for Wyrm Desktop.

/** Forgets the Google account on sign-out. */
internal suspend fun clearGoogleCredentials(context: Context) {
    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
}

suspend fun googleIdToken(activity: Activity, webClientId: String): String {
    check(webClientId.isNotBlank() && !webClientId.startsWith("REPLACE_")) {
        "Google login is not configured."
    }
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    val result = CredentialManager.create(activity).getCredential(activity, request)
    val credential = result.credential
    check(
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) { "Google account unavailable." }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
