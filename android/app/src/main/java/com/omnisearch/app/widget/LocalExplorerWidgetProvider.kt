package com.omnisearch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.omnisearch.app.MainActivity
import com.omnisearch.app.R

class LocalExplorerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_local_explorer)
            
            fun getPendingIntent(action: String): PendingIntent {
                val intent = Intent(context, MainActivity::class.java).apply {
                    this.action = "com.omnisearch.app.ACTION_OPEN_LOCAL_EXPLORER"
                    putExtra("EXPLORER_CATEGORY", action)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                return PendingIntent.getActivity(
                    context,
                    action.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            
            views.setOnClickPendingIntent(R.id.btn_internal, getPendingIntent("internal"))
            views.setOnClickPendingIntent(R.id.btn_downloads, getPendingIntent("downloads"))
            views.setOnClickPendingIntent(R.id.btn_images, getPendingIntent("images"))
            views.setOnClickPendingIntent(R.id.btn_documents, getPendingIntent("documents"))
            views.setOnClickPendingIntent(R.id.btn_music, getPendingIntent("music"))
            
            WidgetThemeUtil.applyThemeLayoutBg(
            context,
            views,
            R.id.widget_explorer_bg,
            listOf(
                R.id.widget_explorer_text_1,
                R.id.widget_explorer_text_2,
                R.id.widget_explorer_text_3,
                R.id.widget_explorer_text_4,
                R.id.widget_explorer_text_5
            ),
            emptyList() // do not tint icons
        )
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
