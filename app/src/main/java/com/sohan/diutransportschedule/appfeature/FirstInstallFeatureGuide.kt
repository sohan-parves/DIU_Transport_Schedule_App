package com.sohan.diutransportschedule.appfeature

import android.content.Context

private const val PREF_WELCOME_GUIDE = "welcome_guide_prefs"
private const val KEY_WELCOME_GUIDE_SHOWN = "welcome_guide_shown"
const val FIRST_INSTALL_GUIDE_ENABLED = true

object AppFeatureGuideContent {
    val model = AppFeatureGuideModel(
        title = "Welcome to DIU Transport Schedule",
        subtitle = "First install guide: set up in 1 minute",
        items = listOf(
            AppFeatureGuideItem(
                title = "Step 1: Select your routes",
                description = "Go to Settings and select one daily route and one Friday route. Daily route works for regular days, and Friday route is used only on Friday."
            ),
            AppFeatureGuideItem(
                title = "Step 2: Turn on reminders",
                description = "Enable Notify Me from Settings to get route-based reminders. The app will ask for notification and exact alarm permissions for reliable alerts."
            ),
            AppFeatureGuideItem(
                title = "Step 3: Set sound and vibration",
                description = "Choose ringtone, vibration pattern, and alert duration from Settings. Alert sound and vibration will run for your selected duration."
            ),
            AppFeatureGuideItem(
                title = "Map and live location",
                description = "Open Live Map to view route lines, stops, and your current location. Friday route appears on map only on Friday."
            ),
            AppFeatureGuideItem(
                title = "Home and Notice sync behavior",
                description = "Schedule and notices are cached locally for faster loading. Data checks follow time windows to reduce unnecessary internet usage."
            ),
            AppFeatureGuideItem(
                title = "Offline-friendly use",
                description = "Previously loaded schedule and notice data stay saved on your phone, so you can still view key information even with weak internet."
            )
        )
    )
}

fun shouldShowWelcomeGuide(context: Context): Boolean {
    if (!FIRST_INSTALL_GUIDE_ENABLED) return false

    val prefs = context.getSharedPreferences(PREF_WELCOME_GUIDE, Context.MODE_PRIVATE)
    return !prefs.getBoolean(KEY_WELCOME_GUIDE_SHOWN, false)
}

fun markWelcomeGuideShown(context: Context) {
    context.getSharedPreferences(PREF_WELCOME_GUIDE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_WELCOME_GUIDE_SHOWN, true)
        .apply()
}