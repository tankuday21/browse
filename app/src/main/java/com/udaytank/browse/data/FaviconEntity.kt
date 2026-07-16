package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A cached site icon, keyed by host (v4.1). Populated as you browse — the WebView reports the
 * site's own declared icon, so this is 100% source-direct (never a third-party favicon proxy).
 *
 * Exactly one of [iconUrl] / [iconBytes] is set:
 *  - [iconUrl]: a high-resolution apple-touch-icon URL the page declared (preferred — crisp,
 *    and re-fetched source-direct by the image loader).
 *  - [iconBytes]: the decoded favicon bitmap (PNG) WebView handed us, used when the site declares
 *    no touch icon. Lower-res but still the real site icon.
 *
 * Incognito browsing NEVER writes here (guarded at capture time) — a private tab must leave no
 * trace of the hosts it visited.
 */
@Entity(tableName = "favicons")
data class FaviconEntity(
    @PrimaryKey val host: String,
    val iconUrl: String?,
    val iconBytes: ByteArray?,
    val updatedAt: Long,
) {
    // ByteArray needs structural equals/hashCode (Room + data-class correctness).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaviconEntity) return false
        return host == other.host &&
            iconUrl == other.iconUrl &&
            iconBytes.contentEquals(other.iconBytes) &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + (iconUrl?.hashCode() ?: 0)
        result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Dao
interface FaviconDao {
    @Query("SELECT * FROM favicons")
    fun observeAll(): Flow<List<FaviconEntity>>

    @Query("SELECT * FROM favicons WHERE host = :host")
    suspend fun get(host: String): FaviconEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FaviconEntity)
}
