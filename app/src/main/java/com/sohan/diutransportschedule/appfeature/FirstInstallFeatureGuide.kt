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
                title = "Daily and Friday route setup",
                description = "You can set one route for regular days and a separate route for Friday, so reminders follow the correct schedule for each day."
            ),
            AppFeatureGuideItem(
                title = "Route-based reminder alerts",
                description = "Reminder notifications work from your selected route, with sound, vibration, and notification controls available from settings."
            ),
            AppFeatureGuideItem(
                title = "Fast search and quick access",
                description = "Find schedules quickly by route number, stop name, or keyword, and move between tabs smoothly for faster use."
            ),
            AppFeatureGuideItem(
                title = "Map with live location",
                description = "Open the map to view route lines, stops, and your current location for easier navigation during travel."
            ),
            AppFeatureGuideItem(
                title = "Smart notices and updates",
                description = "Important notices are synced and stored locally, and the app checks for schedule updates at fixed time slots to reduce extra data use."
            ),
            AppFeatureGuideItem(
                title = "Offline-friendly experience",
                description = "Previously loaded schedule and notice data stay saved on your device, so you can still view important information even with poor internet."
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