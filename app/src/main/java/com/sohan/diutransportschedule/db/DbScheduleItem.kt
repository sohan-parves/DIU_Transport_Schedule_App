package com.sohan.diutransportschedule.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_items")
data class DbScheduleItem(
    @PrimaryKey val id: String,
    val routeNo: String,
    val routeName: String,
    val startTimesJson: String,
    val departureTimesJson: String,
    val routeDetails: String
)