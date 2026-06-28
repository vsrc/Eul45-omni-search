package com.omnisearch.app.data

import org.json.JSONArray
import org.json.JSONObject

data class SearchResult(
    val name: String,
    val path: String,
    val extension: String,
    val size: Long,
    val createdUnix: Long,
    val modifiedUnix: Long,
    val isDirectory: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): SearchResult {
            return SearchResult(
                name = json.optString("name", ""),
                path = json.optString("path", ""),
                extension = json.optString("extension", ""),
                size = json.optLong("size", 0L),
                createdUnix = json.optLong("createdUnix", 0L),
                modifiedUnix = json.optLong("modifiedUnix", 0L),
                isDirectory = json.optBoolean("isDirectory", false)
            )
        }

        fun fromJsonArray(array: JSONArray): List<SearchResult> {
            val list = mutableListOf<SearchResult>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    list.add(fromJson(obj))
                }
            }
            return list
        }
    }
}

data class DuplicateFile(
    val name: String,
    val path: String,
    val size: Long,
    val createdUnix: Long,
    val modifiedUnix: Long
) {
    companion object {
        fun fromJson(json: JSONObject): DuplicateFile {
            return DuplicateFile(
                name = json.optString("name", ""),
                path = json.optString("path", ""),
                size = json.optLong("size", 0L),
                createdUnix = json.optLong("createdUnix", 0L),
                modifiedUnix = json.optLong("modifiedUnix", 0L)
            )
        }
    }
}

data class DuplicateGroup(
    val groupId: String,
    val size: Long,
    val totalBytes: Long,
    val fileCount: Int,
    val files: List<DuplicateFile>
) {
    companion object {
        fun fromJson(json: JSONObject): DuplicateGroup {
            val filesArray = json.optJSONArray("files") ?: JSONArray()
            val filesList = mutableListOf<DuplicateFile>()
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.optJSONObject(i)
                if (fileObj != null) {
                    filesList.add(DuplicateFile.fromJson(fileObj))
                }
            }
            return DuplicateGroup(
                groupId = json.optString("groupId", ""),
                size = json.optLong("size", 0L),
                totalBytes = json.optLong("totalBytes", 0L),
                fileCount = json.optInt("fileCount", 0),
                files = filesList
            )
        }

        fun fromJsonArray(array: JSONArray): List<DuplicateGroup> {
            val list = mutableListOf<DuplicateGroup>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    list.add(fromJson(obj))
                }
            }
            return list
        }
    }
}

data class DriveInfo(
    val letter: String,
    val path: String,
    val filesystem: String,
    val driveType: String,
    val isNtfs: Boolean,
    val canOpenVolume: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): DriveInfo {
            return DriveInfo(
                letter = json.optString("letter", ""),
                path = json.optString("path", ""),
                filesystem = json.optString("filesystem", ""),
                driveType = json.optString("driveType", ""),
                isNtfs = json.optBoolean("isNtfs", false),
                canOpenVolume = json.optBoolean("canOpenVolume", false)
            )
        }

        fun fromJsonArray(array: JSONArray): List<DriveInfo> {
            val list = mutableListOf<DriveInfo>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    list.add(fromJson(obj))
                }
            }
            return list
        }
    }
}

data class IndexStatus(
    val indexing: Boolean,
    val ready: Boolean,
    val indexedCount: Long,
    val lastError: String?
) {
    companion object {
        fun fromJson(json: JSONObject): IndexStatus {
            return IndexStatus(
                indexing = json.optBoolean("indexing", false),
                ready = json.optBoolean("ready", false),
                indexedCount = json.optLong("indexedCount", 0L),
                lastError = if (json.isNull("lastError")) null else json.optString("lastError")
            )
        }
    }
}
