package com.omnisearch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.omnisearch.app.MainActivity
import com.omnisearch.app.R

class SearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_MODE) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val currentIsPcMode = prefs.getBoolean(KEY_IS_PC_MODE, true)
            prefs.edit().putBoolean(KEY_IS_PC_MODE, !currentIsPcMode).apply()
            
            // Update all widgets
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, SearchWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_MODE = "com.omnisearch.app.widget.ACTION_TOGGLE_SEARCH_MODE"
        const val PREF_NAME = "omnisearch_search_widget_prefs"
        const val KEY_IS_PC_MODE = "is_pc_mode"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val isPcMode = prefs.getBoolean(KEY_IS_PC_MODE, true)

            val views = RemoteViews(context.packageName, R.layout.widget_search)

            WidgetThemeUtil.applyThemeImageBgCustom(
                context,
                views,
                R.id.widget_search_bg,
                listOf(R.id.widget_search_text),
                listOf(R.id.widget_icon_toggle),
                R.drawable.widget_search_bg_dark,
                R.drawable.widget_search_bg_light
            )

            // Custom coloring for the search icon (widget_icon_left)
            val widgetPrefs = context.getSharedPreferences("omnisearch_widget_theme", Context.MODE_PRIVATE)
            val currentTint = widgetPrefs.getInt("widget_tint", android.graphics.Color.TRANSPARENT)
            val isAppDark = WidgetThemeUtil.isDarkTheme(context)
            
            // If the user has selected the Blue tint, make the icon white/dark so it doesn't blend.
            // Otherwise, make it Blue.
            val searchIconColor = if (currentTint == android.graphics.Color.parseColor("#1976D2")) {
                if (isAppDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1E1E1E")
            } else {
                android.graphics.Color.parseColor("#1976D2") // Accent Blue
            }
            views.setInt(R.id.widget_icon_left, "setColorFilter", searchIconColor)

            // Set UI based on mode
            if (isPcMode) {
                views.setTextViewText(R.id.widget_search_text, "Search PC files...")
                views.setImageViewResource(R.id.widget_icon_toggle, R.drawable.ic_widget_computer)
            } else {
                views.setTextViewText(R.id.widget_search_text, "Search phone files...")
                views.setImageViewResource(R.id.widget_icon_toggle, R.drawable.ic_widget_phone)
            }

            // Set up toggle click action
            val toggleIntent = Intent(context, SearchWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_MODE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                toggleIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_icon_toggle, togglePendingIntent)

            // Set up search click action (launch app)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.omnisearch.app.ACTION_OPEN_OMNI_SEARCH"
                putExtra("TARGET", if (isPcMode) "PC" else "LOCAL")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                (if (isPcMode) 100 else 101), // Different request codes to avoid intent caching issues
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_search_text, launchPendingIntent)
            // Also make the root clickable just in case they tap near the edge
            views.setOnClickPendingIntent(R.id.widget_root, launchPendingIntent)
            // Ensure clicking toggle doesn't trigger the root
            views.setOnClickPendingIntent(R.id.widget_icon_toggle, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
