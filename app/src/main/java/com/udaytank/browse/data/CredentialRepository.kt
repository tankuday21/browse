package com.udaytank.browse.data

import com.udaytank.browse.browser.CredentialHostMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A saved login with its password decrypted — only ever held transiently (fill/reveal). */
data class DecryptedCredential(
    val id: Long,
    val host: String,
    val username: String,
    val password: String,
)

/**
 * The per-Orbit password store (v4.7). Composes [CredentialDao] with a [CredentialCipher] so
 * passwords are encrypted at rest and only decrypted on demand (fill or an explicit reveal).
 * Callers are responsible for the incognito gate — this class never inspects tab state.
 */
class CredentialRepository(
    private val dao: CredentialDao,
    private val cipher: CredentialCipher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Raw rows for one Orbit (management list); reveal a password with [reveal]. */
    fun observeForOrbit(orbitId: Long): Flow<List<CredentialEntity>> = dao.observeForOrbit(orbitId)

    /** Encrypt + store a login. Returns false (no write) if encryption is unavailable. */
    suspend fun save(orbitId: Long, host: String, username: String, password: String, now: Long): Boolean =
        withContext(io) {
            if (host.isBlank() || password.isEmpty()) return@withContext false
            val enc = cipher.encrypt(password) ?: return@withContext false
            dao.upsert(
                CredentialEntity(
                    orbitId = orbitId,
                    host = host,
                    username = username,
                    passwordCipher = enc.ciphertext,
                    iv = enc.iv,
                    updatedAt = now,
                ),
            )
            true
        }

    /** Decrypted logins for a host in one Orbit (exact re-lookup at fill time); undecryptable rows dropped. */
    suspend fun credentialsForHost(orbitId: Long, host: String): List<DecryptedCredential> =
        withContext(io) {
            dao.getForOrbitAndHost(orbitId, host).mapNotNull { it.toDecrypted() }
        }

    /**
     * Decrypted logins whose stored host fills on [pageHost] — same registrable domain (v6.5,
     * cross-subdomain). Exact-host matches rank first (see [CredentialHostMatch.rankHosts]); within
     * a host, recency order from the DAO is preserved. Only matching rows are decrypted; rows that
     * fail to decrypt are dropped.
     */
    suspend fun credentialsForSite(orbitId: Long, pageHost: String): List<DecryptedCredential> =
        withContext(io) {
            // matches() = exact-host OR same registrable domain. The exact-host arm keeps fill from
            // ever being weaker than the pre-v6.5 behaviour for hosts that have no registrable
            // domain (IP literals, `localhost`, a bare public suffix like `wordpress.com`).
            val rows = dao.getAllForOrbit(orbitId).filter {
                CredentialHostMatch.matches(pageHost, it.host)
            }
            val hostRank = CredentialHostMatch.rankHosts(pageHost, rows.map { it.host })
                .withIndex().associate { (i, h) -> h to i }
            rows.sortedBy { hostRank[it.host] ?: Int.MAX_VALUE }.mapNotNull { it.toDecrypted() }
        }

    /** Reveal a single row's password (management screen). Null if decryption fails. */
    suspend fun reveal(entity: CredentialEntity): String? =
        withContext(io) { cipher.decrypt(entity.passwordCipher, entity.iv) }

    suspend fun delete(id: Long) = withContext(io) { dao.deleteById(id) }
    suspend fun deleteForOrbit(orbitId: Long) = withContext(io) { dao.deleteForOrbit(orbitId) }
    suspend fun clearAll() = withContext(io) { dao.clearAll() }

    private fun CredentialEntity.toDecrypted(): DecryptedCredential? =
        cipher.decrypt(passwordCipher, iv)?.let { DecryptedCredential(id, host, username, it) }
}
