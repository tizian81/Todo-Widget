package de.tizian.todowidget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var items: List<String> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { items = Prefs.tasks(ctx) }
        override fun onDestroy() {}
        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_item)
            val text = items.getOrNull(position) ?: return views
            views.setTextViewText(R.id.text, text)
            views.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra(TodoWidgetProvider.EXTRA_TEXT, text)
            )
            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}
