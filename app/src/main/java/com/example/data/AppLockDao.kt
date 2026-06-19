package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM locked_apps ORDER BY appName ASC")
    fun getAllAppsFlow(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): LockedApp?

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1")
    suspend fun getLockedAppsList(): List<LockedApp>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1")
    fun getLockedAppsFlow(): Flow<List<LockedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: LockedApp)

    @Delete
    suspend fun delete(app: LockedApp)
}
