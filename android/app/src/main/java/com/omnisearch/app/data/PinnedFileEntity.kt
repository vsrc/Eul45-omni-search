package com.omnisearch.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_files")
data class PinnedFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val createdUnix: Long,
    val modifiedUnix: Long,
    val isDirectory: Boolean,
    val pinnedAt: Long = System.currentTimeMillis(),
    val fileData: String? = null
)
