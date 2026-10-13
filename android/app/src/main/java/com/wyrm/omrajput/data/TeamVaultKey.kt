package com.wyrm.omrajput.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The key the team vault (TeamService's saved Team IDs and Auth Keys) is
 * encrypted under: an AES key in the Android Keystore, never leaving it.
 * Moved here unchanged from TeamService.key() (2026-10-11) so Wyrm Desktop can
 * compile TeamService as it is and give its own TeamVaultKey (Windows DPAPI).
 */
internal object TeamVaultKey {
    @Suppress("UNUSED_PARAMETER")
    fun key(context: Context, alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
