package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One saved login (v4.7, Passwords Phase 1). The password is stored **encrypted** — [passwordCipher]
 * is AES-256-GCM ciphertext and [iv] its nonce, both produced by CredentialCipher using a key held
 * in the AndroidKeyStore (never exported). Host + username are plaintext for lookup/display only.
 *
 * Per-Orbit ([orbitId]) like the rest of the container-isolated data. The unique index on
 * (orbitId, host, username) means re-saving the same login in the same Orbit REPLACES it (a
 * password change updates the row rather than duplicating).
 */
@Entity(
    tableName = "credentials",
    indices = [Index(value = ["orbitId", "host", "username"], unique = true)],
)
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orbitId: Long,
    val host: String,
    val username: String,
    val passwordCipher: ByteArray,
    val iv: ByteArray,
    val updatedAt: Long,
) {
    // ByteArray needs structural equals/hashCode (Kotlin data classes use reference identity).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialEntity) return false
        return id == other.id && orbitId == other.orbitId && host == other.host &&
            username == other.username && passwordCipher.contentEquals(other.passwordCipher) &&
            iv.contentEquals(other.iv) && updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + orbitId.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordCipher.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Dao
interface CredentialDao {
    /** One Orbit's saved logins, newest first. */
    @Query("SELECT * FROM credentials WHERE orbitId = :orbitId ORDER BY updatedAt DESC")
    fun observeForOrbit(orbitId: Long): Flow<List<CredentialEntity>>

    /** Saved logins for a host within one Orbit (drives the fill prompt). */
    @Query("SELECT * FROM credentials WHERE orbitId = :orbitId AND host = :host ORDER BY updatedAt DESC")
    suspend fun getForOrbitAndHost(orbitId: Long, host: String): List<CredentialEntity>

    /** Insert or replace on the (orbitId, host, username) unique index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Purges a deleted Orbit's logins — hard isolation requirement. */
    @Query("DELETE FROM credentials WHERE orbitId = :orbitId")
    suspend fun deleteForOrbit(orbitId: Long)

    /** Global wipe across every Orbit (Black Hole panic-wipe). */
    @Query("DELETE FROM credentials")
    suspend fun clearAll()
}
