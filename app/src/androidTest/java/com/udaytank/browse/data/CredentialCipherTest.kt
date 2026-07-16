package com.udaytank.browse.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** AndroidKeyStore is only available on a device/emulator, so this is an instrumented test. */
@RunWith(AndroidJUnit4::class)
class CredentialCipherTest {

    private val cipher = KeystoreCredentialCipher()

    @Test
    fun encryptDecryptRoundTrips() {
        val enc = cipher.encrypt("hunter2")
        assertNotNull(enc)
        enc!!
        // Ciphertext must not be the plaintext bytes.
        assertTrue(!enc.ciphertext.contentEquals("hunter2".toByteArray()))
        assertEquals("hunter2", cipher.decrypt(enc.ciphertext, enc.iv))
    }

    @Test
    fun tamperedCiphertextFailsClosed() {
        val enc = cipher.encrypt("secret")!!
        val tampered = enc.ciphertext.clone().also { it[0] = (it[0] + 1).toByte() }
        // GCM auth tag mismatch → decrypt returns null, never throws.
        assertNull(cipher.decrypt(tampered, enc.iv))
    }

    @Test
    fun distinctIvsPerEncryption() {
        val a = cipher.encrypt("same")!!
        val b = cipher.encrypt("same")!!
        // GCM nonce is random per call, so identical plaintext yields different ciphertext.
        assertTrue(!a.iv.contentEquals(b.iv) || !a.ciphertext.contentEquals(b.ciphertext))
    }
}
