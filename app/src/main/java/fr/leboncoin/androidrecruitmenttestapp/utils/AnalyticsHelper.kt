package fr.leboncoin.androidrecruitmenttestapp.utils

import android.content.Context
import androidx.core.content.edit

class AnalyticsHelper {

    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context
    }

    fun trackSelection(itemId: String) {
        context?.let { activity ->
            val prefs = activity.getSharedPreferences(ANALYTICS_SHARED_PREFS, Context.MODE_PRIVATE)
            prefs.edit { putString(SELECTED_ITEM_KEY, itemId) }

            // Simulate some analytics logging
            println("Analytics: User selected item - $itemId")
        }
    }

    fun trackScreenView(screenName: String) {
        context?.let {
            // Simulate some analytics logging
            println("Analytics: Screen viewed - $screenName")
        }
    }
}

private const val ANALYTICS_SHARED_PREFS = "analytics_prefs"
private const val SELECTED_ITEM_KEY = "selected_item"