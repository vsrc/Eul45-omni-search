package com.omnisearch.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File

object LocalAudioArtworkCache {
    private val cache = LruCache<String, Bitmap>(24)
    private val missing = mutableSetOf<String>()

    fun get(file: File): Bitmap? {
        val key = keyFor(file)
        cache.get(key)?.let { return it }
        if (missing.contains(key)) return null

        return try {
            val bitmap =
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(file.absolutePath)
                    val art = retriever.embeddedPicture ?: return@use null
                    BitmapFactory.decodeByteArray(art, 0, art.size)
                }
            if (bitmap != null) {
                cache.put(key, bitmap)
            } else {
                missing.add(key)
            }
            bitmap
        } catch (_: Throwable) {
            missing.add(key)
            null
        }
    }

    private fun keyFor(file: File): String =
        "${file.absolutePath}:${file.lastModified()}:${file.length()}"
}
