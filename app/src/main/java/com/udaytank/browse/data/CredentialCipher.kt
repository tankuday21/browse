package com.udaytank.browse.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ciphertext + the GCM nonce it was produced with; both are stored on the credential row. */
class EncryptedBytes(val ciphertext: ByteArray, val iv: ByteArray)

/**
 * Encrypts/decrypts credential secrets. An interface so the ViewModel/repository stay unit-testable
 * (the Keystore-backed impl needs a real device); production uses [KeystoreCredentialCipher].
 */
interface CredentialCipher {
    /** Encrypt [plain]; null if the crypto/Keystore is unavailable (caller degrades gracefully). */
    fun encrypt(plain: String): EncryptedBytes?

    /** Decrypt; null on tamper/failure or an invalidated key (row treated as unusable, never crash). */
    fun decrypt(ciphertext: ByteArray, iv: ByteArray): String?
}

/**
 * AES-256-GCM with a key held in the **AndroidKeyStore** (hardware-backed where available), never
 * exported off the device. The key is created lazily under a fixed alias on first use. Phase 1
 * does not require per-use user authentication (the incognito biometric lock is a separate
 * mechanism); a future phase can gate reveals behind biometrics.
 */
class KeystoreCredentialCipher : CredentialCipher {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): EncryptedBytes? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        EncryptedBytes(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), cipher.iv)
    }.getOrNull()

    override fun decrypt(ciphertext: ByteArray, iv: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "andromeda_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
