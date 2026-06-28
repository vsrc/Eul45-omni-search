package com.omnisearch.app.ui

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

object LocalMusicPlayerManager {
    private const val TAG = "MusicPlayer"
    private var mediaPlayer: MediaPlayer? = null
    
    var currentPlayingFile by mutableStateOf<File?>(null)
        private set
    
    var isPlaying by mutableStateOf(false)
        private set

    var isLooping by mutableStateOf(false)
        private set

    var currentPlaybackPosition by mutableIntStateOf(0)
    
    var duration by mutableIntStateOf(0)
        private set

    var playbackList by mutableStateOf<List<File>>(emptyList())
        private set

    var currentIndex by mutableIntStateOf(0)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPlaybackPosition = player.currentPosition
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    fun play(file: File, list: List<File> = emptyList()) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            val newList = if (list.isNotEmpty()) list else listOf(file)
            playbackList = newList
            val idx = newList.indexOfFirst { it.absolutePath == file.absolutePath }
            currentIndex = if (idx != -1) idx else 0
            currentPlayingFile = file

            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    if (isLooping) {
                        seekTo(0)
                        start()
                    } else {
                        next()
                    }
                }
                start()
            }
            mediaPlayer = player
            duration = player.duration
            currentPlaybackPosition = 0
            isPlaying = true
            
            handler.removeCallbacks(progressUpdater)
            handler.post(progressUpdater)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: ${file.absolutePath}", e)
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                handler.removeCallbacks(progressUpdater)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                isPlaying = true
                handler.post(progressUpdater)
            }
        }
    }

    fun stop() {
        mediaPlayer?.let { player ->
            player.stop()
            player.release()
        }
        mediaPlayer = null
        isPlaying = false
        currentPlayingFile = null
        currentPlaybackPosition = 0
        duration = 0
        handler.removeCallbacks(progressUpdater)
    }

    fun next() {
        if (playbackList.isEmpty()) return
        val nextIndex = (currentIndex + 1) % playbackList.size
        play(playbackList[nextIndex], playbackList)
    }

    fun previous() {
        if (playbackList.isEmpty()) return
        var prevIndex = currentIndex - 1
        if (prevIndex < 0) {
            prevIndex = playbackList.size - 1
        }
        play(playbackList[prevIndex], playbackList)
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs)
            currentPlaybackPosition = positionMs
        }
    }

    fun toggleLoop() {
        isLooping = !isLooping
        mediaPlayer?.isLooping = isLooping
    }
}
