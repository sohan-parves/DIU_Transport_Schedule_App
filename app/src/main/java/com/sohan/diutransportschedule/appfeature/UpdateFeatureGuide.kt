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
                title = "Update alerts",
                description = "Whenever the schedule changes, you’ll see an update message with the exact date and time of the update."
            ),
            AppFeatureGuideItem(
                title = "Cleaner feature guide flow",
                description = "First install welcome guide and app update guide are now handled separately for a better experience."
            ),
            AppFeatureGuideItem(
                title = "Same familiar design",
                description = "The update guide uses the same card design and bottom-sheet style, so everything feels consistent."
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