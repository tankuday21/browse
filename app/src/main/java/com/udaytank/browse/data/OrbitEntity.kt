package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "orbits")
data class OrbitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    /** Stable ProfileStore profile name. Generated once at creation; never reused. */
    val profileKey: String,
)

@Dao
interface OrbitDao {
    @Query("SELECT * FROM orbits ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<OrbitEntity>>

    @Query("SELECT * FROM orbits ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<OrbitEntity>

    @Query("SELECT * FROM orbits WHERE id = :id")
    suspend fun getById(id: Long): OrbitEntity?

    @Query("SELECT COUNT(*) FROM orbits")
    suspend fun count(): Int

    @Insert
    suspend fun insert(orbit: OrbitEntity): Long

    @Update
    suspend fun update(orbit: OrbitEntity)

    @Query("DELETE FROM orbits WHERE id = :id")
    suspend fun deleteById(id: Long)
}
