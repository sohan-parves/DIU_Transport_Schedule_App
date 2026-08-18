package com.sohan.diutransportschedule.appfeature

import android.content.Context
import com.sohan.diutransportschedule.BuildConfig

const val PREF_FEATURE_GUIDE = "feature_guide_prefs"
const val KEY_FEATURE_GUIDE_LAST_VERSION = "feature_guide_last_version"
const val UPDATE_GUIDE_ENABLED = false

object AppUpdateFeatureGuideContent {
    val model = AppFeatureGuideModel(
        title = "What’s new in this update",
        subtitle = "Update guide: important changes you should know",
        items = listOf(
            AppFeatureGuideItem(
                title = "Schedule last-updated date",
                description = "The Daily Schedule header now shows when the schedule was last updated."
            ),
            AppFeatureGuideItem(
                title = "Faster notice updates",
                description = "When a new notice arrives through FCM, the Notice page refreshes automatically and shows it right away."
            ),
            AppFeatureGuideItem(
                title = "Improved notification reliability",
                description = "Notification and background-activity guidance has been improved to help you receive transport updates reliably."
            )
        )
    )
}

fun shouldShowUpdateGuide(context: Context): Boolean {
    if (!UPDATE_GUIDE_ENABLED) return false

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
