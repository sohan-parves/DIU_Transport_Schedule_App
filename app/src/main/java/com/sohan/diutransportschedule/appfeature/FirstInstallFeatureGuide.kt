package com.sohan.diutransportschedule.appfeature

import android.content.Context

private const val PREF_WELCOME_GUIDE = "welcome_guide_prefs"
private const val KEY_WELCOME_GUIDE_SHOWN = "welcome_guide_shown"

object AppFeatureGuideContent {
    val model = AppFeatureGuideModel(
        title = "Welcome to DIU Transport Schedule",
        subtitle = "Quick guide to help you get started",
        items = listOf(
            AppFeatureGuideItem(
                title = "Fast schedule loading",
                description = "App opens quickly and only checks for updates at specific times to reduce data usage and improve performance."
            ),
            AppFeatureGuideItem(
                title = "Search any route instantly",
                description = "Easily find your bus by route number, stop name, or keywords using the search bar."
            ),
            AppFeatureGuideItem(
                title = "Smart notice system",
                description = "Important notices are automatically synced from the home screen and saved locally for fast access."
            ),
            AppFeatureGuideItem(
                title = "Reminder notifications",
                description = "Set reminders before bus departure time and get notified reliably with sound and alerts."
            ),
            AppFeatureGuideItem(
                title = "Map & live location",
                description = "View route paths, stops, and your current location on the map for better navigation."
            ),
            AppFeatureGuideItem(
                title = "Offline access",
                description = "Previously loaded schedules and notices are stored locally, so you can still view them without internet."
            )
        )
    )
}

fun shouldShowWelcomeGuide(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREF_WELCOME_GUIDE, Context.MODE_PRIVATE)
    return !prefs.getBoolean(KEY_WELCOME_GUIDE_SHOWN, false)
}

fun markWelcomeGuideShown(context: Context) {
    context.getSharedPreferences(PREF_WELCOME_GUIDE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_WELCOME_GUIDE_SHOWN, true)
        .apply()
}