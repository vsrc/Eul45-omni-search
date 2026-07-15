package com.omnisearch.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.RemoteViews
import com.omnisearch.app.R

object WidgetThemeUtil {

    private const val PREFS_NAME = "omnisearch_widget_theme"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_WIDGET_ALPHA = "widget_alpha"
    private const val KEY_WIDGET_TINT = "widget_tint"

    /**
     * Save the current app theme mode to SharedPreferences so widgets can read it
     * without needing DataStore (which conflicts in BroadcastReceiver contexts).
     */
    fun saveThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun saveWidgetAppearance(context: Context, alpha: Int, tintColor: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_WIDGET_ALPHA, alpha)
            .putInt(KEY_WIDGET_TINT, tintColor)
            .apply()
    }

    fun isDarkTheme(context: Context): Boolean {
        val themeMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

        return when (themeMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> {
                // Follow system
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    // Gradient tint IDs (positive values to distinguish from solid colors which are negative due to 0xFF alpha)
    const val GRADIENT_CRIMSON = 1
    const val GRADIENT_SUNSET = 2
    const val GRADIENT_OCEAN = 3
    const val GRADIENT_BERRY = 4
    const val GRADIENT_FOREST = 5
    const val GRADIENT_TWILIGHT = 6

    fun isGradientTint(tint: Int): Boolean = tint in 1..100

    private fun getGeneralGradientRes(gradientId: Int): Int? = when (gradientId) {
        GRADIENT_CRIMSON -> R.drawable.widget_bg_gradient_crimson
        GRADIENT_SUNSET -> R.drawable.widget_bg_gradient_sunset
        GRADIENT_OCEAN -> R.drawable.widget_bg_gradient_ocean
        GRADIENT_BERRY -> R.drawable.widget_bg_gradient_berry
        GRADIENT_FOREST -> R.drawable.widget_bg_gradient_forest
        GRADIENT_TWILIGHT -> R.drawable.widget_bg_gradient_twilight
        else -> null
    }

    private fun getSearchGradientRes(gradientId: Int): Int? = when (gradientId) {
        GRADIENT_CRIMSON -> R.drawable.widget_search_bg_gradient_crimson
        GRADIENT_SUNSET -> R.drawable.widget_search_bg_gradient_sunset
        GRADIENT_OCEAN -> R.drawable.widget_search_bg_gradient_ocean
        GRADIENT_BERRY -> R.drawable.widget_search_bg_gradient_berry
        GRADIENT_FOREST -> R.drawable.widget_search_bg_gradient_forest
        GRADIENT_TWILIGHT -> R.drawable.widget_search_bg_gradient_twilight
        else -> null
    }

    /**
     * Apply theme to a widget with an ImageView as background.
     */
    fun applyThemeImageBg(context: Context, views: RemoteViews, bgImageViewId: Int, textIds: List<Int>, iconIds: List<Int>, isMusicWidget: Boolean = false) {
        val isDark = isDarkTheme(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alpha = prefs.getInt(KEY_WIDGET_ALPHA, 255)
        var tint = prefs.getInt(KEY_WIDGET_TINT, Color.TRANSPARENT)
        if (isMusicWidget && tint == Color.TRANSPARENT) {
            tint = GRADIENT_CRIMSON
        }

        if (isGradientTint(tint)) {
            val gradientRes = getGeneralGradientRes(tint)
            if (gradientRes != null) {
                views.setImageViewResource(bgImageViewId, gradientRes)
            } else {
                views.setImageViewResource(bgImageViewId, if (isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            }
            views.setInt(bgImageViewId, "setImageAlpha", alpha)
            // Clear any lingering solid color filter when switching to a gradient
            views.setInt(bgImageViewId, "setColorFilter", 0)
        } else {
            views.setImageViewResource(bgImageViewId, if (isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            views.setInt(bgImageViewId, "setImageAlpha", alpha)
            views.setInt(bgImageViewId, "setColorFilter", tint)
        }

        // Gradient backgrounds are dark-colored, so always use light text/icons
        val effectiveIsDark = if (isGradientTint(tint)) true else isDark
        applyColors(effectiveIsDark, views, textIds, iconIds)
    }

    /**
     * Apply theme with custom dark/light drawable resources for the background.
     */
    fun applyThemeImageBgCustom(
        context: Context,
        views: RemoteViews,
        bgImageViewId: Int,
        textIds: List<Int>,
        iconIds: List<Int>,
        darkBgRes: Int,
        lightBgRes: Int
    ) {
        val isDark = isDarkTheme(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alpha = prefs.getInt(KEY_WIDGET_ALPHA, 255)
        val tint = prefs.getInt(KEY_WIDGET_TINT, Color.TRANSPARENT)

        val isSearchWidget = (darkBgRes == R.drawable.widget_search_bg_dark)

        if (isGradientTint(tint)) {
            val gradientRes = if (isSearchWidget) getSearchGradientRes(tint) else getGeneralGradientRes(tint)
            if (gradientRes != null) {
                views.setImageViewResource(bgImageViewId, gradientRes)
            } else {
                views.setImageViewResource(bgImageViewId, if (isDark) darkBgRes else lightBgRes)
            }
            views.setInt(bgImageViewId, "setImageAlpha", alpha)
            // Clear any lingering solid color filter when switching to a gradient
            views.setInt(bgImageViewId, "setColorFilter", 0)
        } else {
            views.setImageViewResource(bgImageViewId, if (isDark) darkBgRes else lightBgRes)
            views.setInt(bgImageViewId, "setImageAlpha", alpha)
            views.setInt(bgImageViewId, "setColorFilter", tint)
        }

        val effectiveIsDark = if (isGradientTint(tint)) true else isDark
        applyColors(effectiveIsDark, views, textIds, iconIds)
    }

    /**
     * Legacy method for compatibility. Now everything uses ImageView backgrounds.
     */
    fun applyThemeLayoutBg(context: Context, views: RemoteViews, layoutId: Int, textIds: List<Int>, iconIds: List<Int>) {
        // Now layoutId is actually the bgImageViewId because we changed the layouts!
        applyThemeImageBg(context, views, layoutId, textIds, iconIds)
    }

    private fun applyColors(isDark: Boolean, views: RemoteViews, textIds: List<Int>, iconIds: List<Int>) {
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#1E1E1E")
        val iconColor = if (isDark) Color.WHITE else Color.parseColor("#1E1E1E")

        for (id in textIds) {
            views.setTextColor(id, textColor)
        }
        for (id in iconIds) {
            views.setInt(id, "setColorFilter", iconColor)
        }
    }

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        
        val explorerIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.LocalExplorerWidgetProvider::class.java)
        )
        if (explorerIds.isNotEmpty()) {
            com.omnisearch.app.widget.LocalExplorerWidgetProvider().onUpdate(context, appWidgetManager, explorerIds)
        }
        
        val scanIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.ScanConnectWidgetProvider::class.java)
        )
        for (id in scanIds) {
            com.omnisearch.app.widget.ScanConnectWidgetProvider.updateAppWidget(context, appWidgetManager, id)
        }
        
        val musicIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.MusicWidgetProvider::class.java)
        )
        for (id in musicIds) {
            com.omnisearch.app.widget.MusicWidgetProvider.updateAppWidget(context, appWidgetManager, id)
        }
        
        val musicSmallIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.MusicWidgetSmallProvider::class.java)
        )
        for (id in musicSmallIds) {
            com.omnisearch.app.widget.MusicWidgetSmallProvider.updateAppWidgetSmall(context, appWidgetManager, id)
        }

        val searchIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.omnisearch.app.widget.SearchWidgetProvider::class.java)
        )
        for (id in searchIds) {
            com.omnisearch.app.widget.SearchWidgetProvider.updateAppWidget(context, appWidgetManager, id)
        }
    }
}
