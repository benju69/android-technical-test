package fr.leboncoin.androidrecruitmenttestapp.utils

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight analytics implementation (SharedPreferences + stdout).
 *
 * Scoped as a @Singleton bound in the Hilt graph and holds the
 * @ApplicationContext only, so it can never leak an Activity/Context
 * reference (previously the context was re-assigned on every Activity's
 * onCreate, risking a leak detected by LeakCanary).
 */
@Singleton
class AnalyticsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun trackSelection(itemId: String) {
        val prefs = context.getSharedPreferences(ANALYTICS_SHARED_PREFS, Context.MODE_PRIVATE)
        prefs.edit { putString(SELECTED_ITEM_KEY, itemId) }

        // Simulate some analytics logging
        println("Analytics: User selected item - $itemId")
    }

    fun trackScreenView(screenName: String) {
        // Simulate some analytics logging
        println("Analytics: Screen viewed - $screenName")
    }
}

private const val ANALYTICS_SHARED_PREFS = "analytics_prefs"
private const val SELECTED_ITEM_KEY = "selected_item"
