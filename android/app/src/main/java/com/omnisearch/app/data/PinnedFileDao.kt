package com.omnisearch.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedFileDao {
    @Query("SELECT * FROM pinned_files ORDER BY pinnedAt DESC")
    fun getAllPinnedFiles(): Flow<List<PinnedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedFile(file: PinnedFileEntity)

    @Query("DELETE FROM pinned_files WHERE path = :path")
    suspend fun deletePinnedFileByPath(path: String)

    @Query("SELECT * FROM pinned_files WHERE path = :path LIMIT 1")
    suspend fun getPinnedFileByPath(path: String): PinnedFileEntity?

    @Query("DELETE FROM pinned_files")
    suspend fun clearAll()
}
