package com.udaytank.browse

import com.udaytank.browse.data.CredentialCipher
import com.udaytank.browse.data.CredentialDao
import com.udaytank.browse.data.CredentialEntity
import com.udaytank.browse.data.EncryptedBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A trivial reversible "cipher" for unit tests — ciphertext is the UTF-8 bytes, iv a constant.
 * (The real KeystoreCredentialCipher can only run on a device; its crypto is covered in androidTest.)
 */
class FakeCredentialCipher : CredentialCipher {
    override fun encrypt(plain: String): EncryptedBytes =
        EncryptedBytes(plain.toByteArray(Charsets.UTF_8), byteArrayOf(0))

    override fun decrypt(ciphertext: ByteArray, iv: ByteArray): String =
        String(ciphertext, Charsets.UTF_8)
}

class FakeCredentialDao : CredentialDao {
    val items = MutableStateFlow<List<CredentialEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForOrbit(orbitId: Long): Flow<List<CredentialEntity>> =
        items.map { list -> list.filter { it.orbitId == orbitId }.sortedByDescending { it.updatedAt } }

    override suspend fun getForOrbitAndHost(orbitId: Long, host: String): List<CredentialEntity> =
        items.value.filter { it.orbitId == orbitId && it.host == host }.sortedByDescending { it.updatedAt }

    override suspend fun getAllForOrbit(orbitId: Long): List<CredentialEntity> =
        items.value.filter { it.orbitId == orbitId }.sortedByDescending { it.updatedAt }

    override suspend fun upsert(entity: CredentialEntity) {
        // Mirror the (orbitId, host, username) unique index: replace a matching row.
        val existing = items.value.firstOrNull {
            it.orbitId == entity.orbitId && it.host == entity.host && it.username == entity.username
        }
        items.value = if (existing != null) {
            items.value.map { if (it.id == existing.id) entity.copy(id = existing.id) else it }
        } else {
            items.value + entity.copy(id = nextId++)
        }
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun deleteForOrbit(orbitId: Long) {
        items.value = items.value.filterNot { it.orbitId == orbitId }
    }

    override suspend fun clearAll() {
        items.value = emptyList()
    }
}
