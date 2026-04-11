package com.sohan.diutransportschedule.appfeature

import android.content.Context
import com.sohan.diutransportschedule.BuildConfig

const val PREF_FEATURE_GUIDE = "feature_guide_prefs"
const val KEY_FEATURE_GUIDE_LAST_VERSION = "feature_guide_last_version"

object AppUpdateFeatureGuideContent {
    val model = AppFeatureGuideModel(
        title = "What’s new in this update",
        subtitle = "Update guide: important changes you should know",
        items = listOf(
            AppFeatureGuideItem(
                title = "Update Notice System",
                description = "Now you can recived Notice on Time"
            ),
            AppFeatureGuideItem(
                title = "Route Select System Update",
                description = "Now you can Select Friday Route and recived notification On friday"
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