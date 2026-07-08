package com.omnisearch.app.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.omnisearch.app.MainActivity
import com.omnisearch.app.R
import java.io.File
import org.json.JSONArray

class LocalMusicPlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession =
            MediaSession(this, "OmniSearchLocalMusic").apply {
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() = LocalMusicPlayerManager.resume()
                        override fun onPause() = LocalMusicPlayerManager.pause()
                        override fun onSkipToNext() = LocalMusicPlayerManager.next()
                        override fun onSkipToPrevious() = LocalMusicPlayerManager.previous()
                        override fun onSeekTo(pos: Long) =
                            LocalMusicPlayerManager.seekTo(pos.toInt())
                        override fun onStop() = stopPlayback()
                        override fun onCustomAction(action: String, extras: Bundle?) {
                            when (action) {
                                ACTION_FAVORITE -> toggleCurrentFavorite()
                                ACTION_STOP -> stopPlayback()
                            }
                        }
                    }
                )
                isActive = true
            }
        LocalMusicPlayerManager.setStateListener { updateForegroundNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_PLAY_PAUSE -> {
                if (LocalMusicPlayerManager.isPlaying) {
                    LocalMusicPlayerManager.pause()
                } else {
                    LocalMusicPlayerManager.resume()
                }
            }
            ACTION_PREVIOUS -> LocalMusicPlayerManager.previous()
            ACTION_NEXT -> LocalMusicPlayerManager.next()
            ACTION_FAVORITE -> toggleCurrentFavorite()
            ACTION_STOP -> stopPlayback()
            else -> updateForegroundNotification()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        updateForegroundNotification()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        LocalMusicPlayerManager.setStateListener(null)
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateForegroundNotification() {
        val file = LocalMusicPlayerManager.currentPlayingFile
        if (file == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, file.nameWithoutExtension)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, file.parentFile?.name ?: "Local audio")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, LocalMusicPlayerManager.duration.toLong())
                .apply {
                    LocalAudioArtworkCache.get(file)?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                    }
                }
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (LocalMusicPlayerManager.isPlaying) {
                        PlaybackState.STATE_PLAYING
                    } else {
                        PlaybackState.STATE_PAUSED
                    },
                    LocalMusicPlayerManager.currentPlaybackPosition.toLong(),
                    1f
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_FAVORITE,
                        "Favorite",
                        favoriteIconFor(file)
                    ).build()
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_STOP,
                        "Stop",
                        R.drawable.ic_notification_close
                    ).build()
                )
                .build()
        )

        startForeground(NOTIFICATION_ID, buildNotification(file))
    }

    private fun buildNotification(file: File): Notification {
        val openAppIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_OPEN_LOCAL_MUSIC
                    putExtra(EXTRA_OPEN_LOCAL_MUSIC, true)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        val playPauseIcon =
            if (LocalMusicPlayerManager.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        val playPauseTitle = if (LocalMusicPlayerManager.isPlaying) "Pause" else "Play"
        val artwork = LocalAudioArtworkCache.get(file)
        val isFavorite = isCurrentFavorite(file)
        val favoriteIcon = favoriteIconFor(file)

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(file.nameWithoutExtension)
            .setContentText(file.parentFile?.name ?: "Local audio")
            .setContentIntent(openAppIntent)
            .setLargeIcon(artwork)
            .setOngoing(LocalMusicPlayerManager.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setDeleteIntent(serviceIntent(ACTION_STOP, 6))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                favoriteIcon,
                if (isFavorite) "Unfavorite" else "Favorite",
                serviceIntent(ACTION_FAVORITE, 1)
            )
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                serviceIntent(ACTION_PREVIOUS, 2)
            )
            .addAction(playPauseIcon, playPauseTitle, serviceIntent(ACTION_PLAY_PAUSE, 3))
            .addAction(android.R.drawable.ic_media_next, "Next", serviceIntent(ACTION_NEXT, 4))
            .addAction(
                R.drawable.ic_notification_close,
                "Stop",
                serviceIntent(ACTION_STOP, 5)
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LocalMusicPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun stopPlayback() {
        LocalMusicPlayerManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toggleCurrentFavorite() {
        val file = LocalMusicPlayerManager.currentPlayingFile ?: return
        val prefs = getSharedPreferences("omnisearch_local_explorer", Context.MODE_PRIVATE)
        val paths = LinkedHashSet<String>()
        try {
            val arr = JSONArray(prefs.getString("favorites_paths", "[]") ?: "[]")
            for (i in 0 until arr.length()) paths.add(arr.getString(i))
        } catch (_: Exception) {
        }
        val added = paths.add(file.absolutePath)
        if (!added) paths.remove(file.absolutePath)
        prefs.edit().putString("favorites_paths", JSONArray(paths.toList()).toString()).apply()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                this,
                if (added) "Added to Favorites" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }
        updateForegroundNotification()
    }

    private fun isCurrentFavorite(file: File): Boolean {
        val prefs = getSharedPreferences("omnisearch_local_explorer", Context.MODE_PRIVATE)
        return try {
            val arr = JSONArray(prefs.getString("favorites_paths", "[]") ?: "[]")
            (0 until arr.length()).any { arr.getString(it) == file.absolutePath }
        } catch (_: Exception) {
            false
        }
    }

    private fun favoriteIconFor(file: File): Int =
        if (isCurrentFavorite(file)) {
            R.drawable.ic_notification_favorite
        } else {
            R.drawable.ic_notification_favorite_border
        }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Local music playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Controls for Local Explorer audio playback"
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "local_music_playback"
        private const val NOTIFICATION_ID = 4207
        private const val ACTION_START = "com.omnisearch.app.localmusic.START"
        private const val ACTION_PLAY_PAUSE = "com.omnisearch.app.localmusic.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.omnisearch.app.localmusic.PREVIOUS"
        private const val ACTION_NEXT = "com.omnisearch.app.localmusic.NEXT"
        private const val ACTION_FAVORITE = "com.omnisearch.app.localmusic.FAVORITE"
        private const val ACTION_STOP = "com.omnisearch.app.localmusic.STOP"
        const val ACTION_OPEN_LOCAL_MUSIC = "com.omnisearch.app.localmusic.OPEN_LOCAL_MUSIC"
        const val EXTRA_OPEN_LOCAL_MUSIC = "open_local_music_player"

        fun start(context: Context) {
            val intent = Intent(context, LocalMusicPlaybackService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
