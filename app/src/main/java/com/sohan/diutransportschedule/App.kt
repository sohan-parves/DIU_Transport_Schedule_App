package com.sohan.diutransportschedule

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import com.sohan.diutransportschedule.db.AppDatabase
import com.sohan.diutransportschedule.prefs.UserPrefs
import com.sohan.diutransportschedule.data.repository.ScheduleRepository
import com.sohan.diutransportschedule.sync.VersionStore

class App : Application() {
    lateinit var repo: ScheduleRepository

    override fun onCreate() {
        super.onCreate()

        val prefs = applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val savedDark = when {
            prefs.contains("dark_mode") -> prefs.getBoolean("dark_mode", false)
            prefs.contains("dark") -> prefs.getBoolean("dark", false)
            prefs.contains("darkMode") -> prefs.getBoolean("darkMode", false)
            else -> true
        }
        AppCompatDelegate.setDefaultNightMode(
            if (savedDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        val db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "diu.db"
        ).build()

        repo = ScheduleRepository(
            dao = db.scheduleDao(),
            fs = FirebaseFirestore.getInstance(),
            store = VersionStore(this),
            prefs = UserPrefs(this)   // ✅ prefs package এরটা
        )

        // Subscribe once to admin notice topic so user app can receive pushes.
        try {
            val sp = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            val done = sp.getBoolean("topic_diu_admin_subscribed", false)
            if (!done) {
                FirebaseMessaging.getInstance()
                    .subscribeToTopic("diu_admin")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            sp.edit().putBoolean("topic_diu_admin_subscribed", true).apply()
                        }
                    }
            }
        } catch (_: Throwable) {
        }
    }
}