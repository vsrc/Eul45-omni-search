package com.omnisearch.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

object LocalTrashManager {
    private const val TAG = "LocalTrashManager"
    private const val TRASH_DIR_NAME = "omnisearch_trash"
    private const val META_FILE_NAME = "trash_metadata.json"

    data class TrashItem(
        val id: String,
        val originalPath: String,
        val name: String,
        val size: Long,
        val isDirectory: Boolean,
        val deletedTime: Long
    ) {
        fun toJsonObject(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("originalPath", originalPath)
                put("name", name)
                put("size", size)
                put("isDirectory", isDirectory)
                put("deletedTime", deletedTime)
            }
        }

        companion object {
            fun fromJsonObject(obj: JSONObject): TrashItem {
                return TrashItem(
                    id = obj.getString("id"),
                    originalPath = obj.getString("originalPath"),
                    name = obj.getString("name"),
                    size = obj.getLong("size"),
                    isDirectory = obj.getBoolean("isDirectory"),
                    deletedTime = obj.getLong("deletedTime")
                )
            }
        }
    }

    private fun getTrashDir(context: Context): File {
        val dir = File(context.filesDir, TRASH_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMetaFile(context: Context): File {
        return File(getTrashDir(context), META_FILE_NAME)
    }

    @Synchronized
    fun getTrashItems(context: Context): List<TrashItem> {
        val file = getMetaFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val array = JSONArray(content)
            val list = mutableListOf<TrashItem>()
            for (i in 0 until array.length()) {
                list.add(TrashItem.fromJsonObject(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.deletedTime }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read trash metadata", e)
            emptyList()
        }
    }

    @Synchronized
    private fun saveTrashItems(context: Context, items: List<TrashItem>) {
        val file = getMetaFile(context)
        try {
            val array = JSONArray()
            items.forEach { array.put(it.toJsonObject()) }
            file.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save trash metadata", e)
        }
    }

    @Synchronized
    fun moveToTrash(context: Context, sourceFile: File): Boolean {
        if (!sourceFile.exists()) return false

        val id = UUID.randomUUID().toString()
        val trashDir = getTrashDir(context)
        val destination = File(trashDir, id)

        return try {
            val success = if (sourceFile.renameTo(destination)) {
                true
            } else {
                // If rename fails (e.g. cross-volume), we do a manual copy & delete
                if (sourceFile.isDirectory) {
                    sourceFile.copyRecursively(destination, overwrite = true) && sourceFile.deleteRecursively()
                } else {
                    sourceFile.copyTo(destination, overwrite = true).exists() && sourceFile.delete()
                }
            }

            if (success) {
                val item = TrashItem(
                    id = id,
                    originalPath = sourceFile.absolutePath,
                    name = sourceFile.name,
                    size = if (sourceFile.isDirectory) getFolderSize(sourceFile) else sourceFile.length(),
                    isDirectory = sourceFile.isDirectory,
                    deletedTime = System.currentTimeMillis()
                )
                val currentItems = getTrashItems(context).toMutableList()
                currentItems.add(item)
                saveTrashItems(context, currentItems)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving to trash: ${sourceFile.absolutePath}", e)
            false
        }
    }

    @Synchronized
    fun restoreItem(context: Context, item: TrashItem): Boolean {
        val trashDir = getTrashDir(context)
        val trashFile = File(trashDir, item.id)
        if (!trashFile.exists()) return false

        val destFile = File(item.originalPath)
        // Ensure parent directory exists
        destFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        return try {
            val success = if (trashFile.renameTo(destFile)) {
                true
            } else {
                // Fallback copy
                if (trashFile.isDirectory) {
                    trashFile.copyRecursively(destFile, overwrite = true) && trashFile.deleteRecursively()
                } else {
                    trashFile.copyTo(destFile, overwrite = true).exists() && trashFile.delete()
                }
            }

            if (success) {
                val currentItems = getTrashItems(context).filter { it.id != item.id }
                saveTrashItems(context, currentItems)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring item: ${item.originalPath}", e)
            false
        }
    }

    @Synchronized
    fun deletePermanently(context: Context, item: TrashItem): Boolean {
        val trashDir = getTrashDir(context)
        val trashFile = File(trashDir, item.id)
        
        val success = if (trashFile.exists()) {
            if (trashFile.isDirectory) {
                trashFile.deleteRecursively()
            } else {
                trashFile.delete()
            }
        } else {
            true // already doesn't exist
        }

        if (success) {
            val currentItems = getTrashItems(context).filter { it.id != item.id }
            saveTrashItems(context, currentItems)
        }
        return success
    }

    @Synchronized
    fun emptyTrash(context: Context): Boolean {
        val trashDir = getTrashDir(context)
        val success = trashDir.deleteRecursively()
        
        // Re-create empty trash directory
        if (!trashDir.exists()) {
            trashDir.mkdirs()
        }
        
        saveTrashItems(context, emptyList())
        return success
    }

    private fun getFolderSize(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) {
                    getFolderSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
}
