package com.sohan.diutransportschedule.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DbScheduleItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}