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
        private var editMode = false

        override fun onCreate() {}
        override fun onDataSetChanged() {
            items = Prefs.tasks(ctx)
            editMode = Prefs.editMode(ctx)
        }
        override fun onDestroy() {}
        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_item)
            val text = items.getOrNull(position) ?: return views
            // Welches Vorkommen bei gleichlautenden Aufgaben (0 = erstes)
            val n = TaskLogic.occurrence(items, position)
            views.setTextViewText(R.id.text, text)
            views.setImageViewResource(
                R.id.check,
                if (editMode) R.drawable.ic_edit_active else R.drawable.ic_checkbox
            )
            views.setOnClickFillInIntent(
                R.id.item_root,
                Intent()
                    .putExtra(TodoWidgetProvider.EXTRA_TEXT, text)
                    .putExtra(TodoWidgetProvider.EXTRA_INDEX, n)
            )
            return views
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}
