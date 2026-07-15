package com.omnisearch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.omnisearch.app.R
import com.omnisearch.app.ui.LocalAudioArtworkCache
import com.omnisearch.app.ui.LocalMusicPlaybackService
import com.omnisearch.app.ui.LocalMusicPlayerManager
import org.json.JSONArray

class MusicWidgetSmallProvider : MusicWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidgetSmall(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidgetSmall(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player_small)
            var file = LocalMusicPlayerManager.currentPlayingFile
            var isActuallyPlaying = false

            if (file == null) {
                val prefs = context.getSharedPreferences("omnisearch_local_explorer", Context.MODE_PRIVATE)
                val lastPlayed = prefs.getString("last_played_music_file", null)
                if (lastPlayed != null) {
                    val savedFile = java.io.File(lastPlayed)
                    if (savedFile.exists()) {
                        file = savedFile
                    }
                }
            } else {
                isActuallyPlaying = LocalMusicPlayerManager.isPlaying
            }

            if (file != null) {
                views.setTextViewText(R.id.widget_music_title, file.nameWithoutExtension)
                views.setTextViewText(R.id.widget_music_artist, file.parentFile?.name ?: "Local audio")
                
                val artwork = LocalAudioArtworkCache.get(file)
                if (artwork != null) {
                    views.setImageViewBitmap(R.id.widget_music_art, artwork)
                } else {
                    views.setImageViewResource(R.id.widget_music_art, R.drawable.ic_widget_music)
                }
                
                views.setImageViewResource(
                    R.id.widget_music_play_pause,
                    if (isActuallyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            } else {
                views.setTextViewText(R.id.widget_music_title, "Not playing")
                views.setTextViewText(R.id.widget_music_artist, "OmniSearch")
                views.setImageViewResource(R.id.widget_music_art, R.drawable.ic_widget_music)
                views.setImageViewResource(R.id.widget_music_play_pause, android.R.drawable.ic_media_play)
            }

            // Intents
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (LocalMusicPlayerManager.currentPlayingFile == null) {
                val lastPlayedPath = file?.absolutePath
                val openAppIntent = Intent(context, com.omnisearch.app.MainActivity::class.java).apply {
                    action = LocalMusicPlaybackService.ACTION_OPEN_LOCAL_MUSIC
                    putExtra(LocalMusicPlaybackService.EXTRA_OPEN_LOCAL_MUSIC, true)
                    if (lastPlayedPath != null) putExtra("AUTO_PLAY_FILE_PATH", lastPlayedPath)
                }
                val pendingOpenApp = PendingIntent.getActivity(context, 0, openAppIntent, pendingFlags)
                views.setOnClickPendingIntent(R.id.widget_music_play_pause, pendingOpenApp)
                views.setOnClickPendingIntent(R.id.widget_music_prev, pendingOpenApp)
                views.setOnClickPendingIntent(R.id.widget_music_next, pendingOpenApp)
            } else {
                val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).setAction("com.omnisearch.app.widget.MUSIC_ACTION_PLAY_PAUSE")
                val prevIntent = Intent(context, MusicWidgetProvider::class.java).setAction("com.omnisearch.app.widget.MUSIC_ACTION_PREVIOUS")
                val nextIntent = Intent(context, MusicWidgetProvider::class.java).setAction("com.omnisearch.app.widget.MUSIC_ACTION_NEXT")

                val playPausePending = PendingIntent.getBroadcast(context, 11, playPauseIntent, pendingFlags)
                val prevPending = PendingIntent.getBroadcast(context, 12, prevIntent, pendingFlags)
                val nextPending = PendingIntent.getBroadcast(context, 13, nextIntent, pendingFlags)

                views.setOnClickPendingIntent(R.id.widget_music_play_pause, playPausePending)
                views.setOnClickPendingIntent(R.id.widget_music_prev, prevPending)
                views.setOnClickPendingIntent(R.id.widget_music_next, nextPending)
            }
            
            val openAppBgIntent = Intent(context, com.omnisearch.app.MainActivity::class.java).apply {
                action = LocalMusicPlaybackService.ACTION_OPEN_LOCAL_MUSIC
                putExtra(LocalMusicPlaybackService.EXTRA_OPEN_LOCAL_MUSIC, true)
            }
            val pendingOpenAppBg = PendingIntent.getActivity(context, 15, openAppBgIntent, pendingFlags)
            views.setOnClickPendingIntent(R.id.widget_music_container, pendingOpenAppBg)

            WidgetThemeUtil.applyThemeImageBg(context, views, R.id.widget_music_bg_image,
                listOf(R.id.widget_music_title, R.id.widget_music_artist),
                listOf(R.id.widget_music_prev, R.id.widget_music_play_pause, R.id.widget_music_next),
                isMusicWidget = true
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
