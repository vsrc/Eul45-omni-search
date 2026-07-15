package com.omnisearch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.omnisearch.app.R
import com.omnisearch.app.ui.LocalAudioArtworkCache
import com.omnisearch.app.ui.LocalMusicPlaybackService
import com.omnisearch.app.ui.LocalMusicPlayerManager
import org.json.JSONArray

open class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == "com.omnisearch.app.widget.MUSIC_ACTION_UPDATE") {
            updateAllWidgets(context)
        } else if (action == "com.omnisearch.app.widget.MUSIC_ACTION_FAVORITE") {
            toggleFavorite(context)
            updateAllWidgets(context)
        } else if (action == "com.omnisearch.app.widget.MUSIC_ACTION_PLAY_PAUSE") {
            sendActionToService(context, "com.omnisearch.app.localmusic.PLAY_PAUSE")
            updateAllWidgets(context)
        } else if (action == "com.omnisearch.app.widget.MUSIC_ACTION_PREVIOUS") {
            sendActionToService(context, "com.omnisearch.app.localmusic.PREVIOUS")
            updateAllWidgets(context)
        } else if (action == "com.omnisearch.app.widget.MUSIC_ACTION_NEXT") {
            sendActionToService(context, "com.omnisearch.app.localmusic.NEXT")
            updateAllWidgets(context)
        }
    }
    
    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        onUpdate(context, appWidgetManager, appWidgetIds)
        
        val smallComponentName = ComponentName(context, MusicWidgetSmallProvider::class.java)
        val smallAppWidgetIds = appWidgetManager.getAppWidgetIds(smallComponentName)
        if (smallAppWidgetIds.isNotEmpty()) {
            val smallProvider = MusicWidgetSmallProvider()
            smallProvider.onUpdate(context, appWidgetManager, smallAppWidgetIds)
        }
    }
    
    private fun sendActionToService(context: Context, serviceAction: String) {
        val serviceIntent = Intent(context, LocalMusicPlaybackService::class.java).setAction(serviceAction)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            val openAppIntent = Intent(context, com.omnisearch.app.MainActivity::class.java).apply {
                this.action = LocalMusicPlaybackService.ACTION_OPEN_LOCAL_MUSIC
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LocalMusicPlaybackService.EXTRA_OPEN_LOCAL_MUSIC, true)
            }
            context.startActivity(openAppIntent)
        }
    }

    private fun toggleFavorite(context: Context) {
        val file = LocalMusicPlayerManager.currentPlayingFile ?: return
        val prefs = context.getSharedPreferences("omnisearch_local_explorer", Context.MODE_PRIVATE)
        val paths = java.util.LinkedHashSet<String>()
        try {
            val arr = JSONArray(prefs.getString("favorites_paths", "[]") ?: "[]")
            for (i in 0 until arr.length()) paths.add(arr.getString(i))
        } catch (_: Exception) {
        }
        val added = paths.add(file.absolutePath)
        if (!added) paths.remove(file.absolutePath)
        prefs.edit().putString("favorites_paths", JSONArray(paths.toList()).toString()).apply()
        
        val serviceIntent = Intent(context, LocalMusicPlaybackService::class.java).apply {
            this.action = "com.omnisearch.app.localmusic.START" // just to trigger update in service
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }



    companion object {
        private fun isCurrentFavorite(context: Context, path: String): Boolean {
            val prefs = context.getSharedPreferences("omnisearch_local_explorer", Context.MODE_PRIVATE)
            return try {
                val arr = JSONArray(prefs.getString("favorites_paths", "[]") ?: "[]")
                (0 until arr.length()).any { arr.getString(it) == path }
            } catch (_: Exception) {
                false
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)
            
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
                
                val isFavorite = isCurrentFavorite(context, file.absolutePath)
                views.setImageViewResource(
                    R.id.widget_music_favorite,
                    if (isFavorite) R.drawable.ic_notification_favorite else R.drawable.ic_notification_favorite_border
                )
            } else {
                views.setTextViewText(R.id.widget_music_title, "Not playing")
                views.setTextViewText(R.id.widget_music_artist, "OmniSearch")
                views.setImageViewResource(R.id.widget_music_art, R.drawable.ic_widget_music)
                views.setImageViewResource(R.id.widget_music_play_pause, android.R.drawable.ic_media_play)
                views.setImageViewResource(R.id.widget_music_favorite, R.drawable.ic_notification_favorite_border)
            }

            // Set up intents for buttons
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            
            // If the service is actually completely dead (meaning we loaded from preferences), we want Play/Pause to launch the app
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
                
                val playPausePending = PendingIntent.getBroadcast(context, 1, playPauseIntent, pendingFlags)
                val prevPending = PendingIntent.getBroadcast(context, 2, prevIntent, pendingFlags)
                val nextPending = PendingIntent.getBroadcast(context, 3, nextIntent, pendingFlags)

                views.setOnClickPendingIntent(R.id.widget_music_play_pause, playPausePending)
                views.setOnClickPendingIntent(R.id.widget_music_prev, prevPending)
                views.setOnClickPendingIntent(R.id.widget_music_next, nextPending)
            }
            
            val favIntent = Intent(context, MusicWidgetProvider::class.java).setAction("com.omnisearch.app.widget.MUSIC_ACTION_FAVORITE")
            val favPending = PendingIntent.getBroadcast(context, 4, favIntent, pendingFlags)
            views.setOnClickPendingIntent(R.id.widget_music_favorite, favPending)

            val openAppBgIntent = Intent(context, com.omnisearch.app.MainActivity::class.java).apply {
                action = LocalMusicPlaybackService.ACTION_OPEN_LOCAL_MUSIC
                putExtra(LocalMusicPlaybackService.EXTRA_OPEN_LOCAL_MUSIC, true)
            }
            val pendingOpenAppBg = PendingIntent.getActivity(context, 5, openAppBgIntent, pendingFlags)
            views.setOnClickPendingIntent(R.id.widget_music_container, pendingOpenAppBg)

            WidgetThemeUtil.applyThemeImageBg(context, views, R.id.widget_music_bg_image,
                listOf(R.id.widget_music_title, R.id.widget_music_artist),
                listOf(R.id.widget_music_prev, R.id.widget_music_play_pause, R.id.widget_music_next, R.id.widget_music_favorite),
                isMusicWidget = true
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
