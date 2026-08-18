package com.sohan.diutransportschedule.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_items ORDER BY routeNo ASC")
    fun observeAll(): Flow<List<DbScheduleItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DbScheduleItem>)

    @Query("DELETE FROM schedule_items")
    suspend fun clearAll()

    /** Atomically replace the entire schedule cache.
     *  Old data is only removed inside the same transaction that inserts the new data,
     *  so a crash mid-operation rolls back to the previous state. */
    @Transaction
    suspend fun replaceAll(items: List<DbScheduleItem>) {
        clearAll()
        upsertAll(items)
    }
}