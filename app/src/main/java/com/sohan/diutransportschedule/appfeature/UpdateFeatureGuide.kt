package com.sohan.diutransportschedule.appfeature

import android.content.Context
import com.sohan.diutransportschedule.BuildConfig

const val PREF_FEATURE_GUIDE = "feature_guide_prefs"
const val KEY_FEATURE_GUIDE_LAST_VERSION = "feature_guide_last_version"

object AppUpdateFeatureGuideContent {
    val model = AppFeatureGuideModel(
        title = "What’s new in this update",
        subtitle = "Here’s a quick look at the latest improvements",
        items = listOf(
            AppFeatureGuideItem(
                title = "Separate daily and Friday route selection",
                description = "You can now keep different route selections for regular days and Friday, so each day follows its own saved route."
            ),
            AppFeatureGuideItem(
                title = "Friday-only reminder behavior",
                description = "Friday reminders now use only the Friday route selection, while the rest of the week continues to follow the daily route."
            ),
            AppFeatureGuideItem(
                title = "Friday ALL option",
                description = "An ALL option is now available in the Friday route list, making it easier to clear Friday route selection when needed."
            ),
            AppFeatureGuideItem(
                title = "Manual dismiss notification panel",
                description = "After alert sound or vibration stops, the notification stays in the phone’s notification panel until you remove it manually."
            ),
            AppFeatureGuideItem(
                title = "Volume and power button alert stop",
                description = "While an alert is running, pressing the volume or power button can stop the sound or vibration without removing the notification."
            ),
            AppFeatureGuideItem(
                title = "Smoother map and profile experience",
                description = "Profile route controls, tab switching, map movement, compass behavior, and feature guide interactions have been refined for a smoother overall experience."
            )
        )
    )
}

fun shouldShowUpdateGuide(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREF_FEATURE_GUIDE, Context.MODE_PRIVATE)
    val lastSeenVersion = prefs.getInt(KEY_FEATURE_GUIDE_LAST_VERSION, -1)
    return lastSeenVersion != -1 && lastSeenVersion != BuildConfig.VERSION_CODE
}

fun markUpdateGuideShown(context: Context) {
    context.getSharedPreferences(PREF_FEATURE_GUIDE, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_FEATURE_GUIDE_LAST_VERSION, BuildConfig.VERSION_CODE)
        .apply()
}

fun initializeFeatureGuideVersion(context: Context) {
    val prefs = context.getSharedPreferences(PREF_FEATURE_GUIDE, Context.MODE_PRIVATE)
    if (!prefs.contains(KEY_FEATURE_GUIDE_LAST_VERSION)) {
        prefs.edit()
            .putInt(KEY_FEATURE_GUIDE_LAST_VERSION, BuildConfig.VERSION_CODE)
            .apply()
    }
}