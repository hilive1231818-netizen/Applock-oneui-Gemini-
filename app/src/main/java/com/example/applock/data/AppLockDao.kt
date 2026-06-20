package com.example.applock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM locked_apps")
    fun getAllLockedApps(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): LockedApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: LockedApp)

    @Delete
    suspend fun delete(app: LockedApp)
}
