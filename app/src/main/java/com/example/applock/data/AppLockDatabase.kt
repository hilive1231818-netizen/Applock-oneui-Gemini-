package com.example.applock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LockedApp::class], version = 2, exportSchema = false)
abstract class AppLockDatabase : RoomDatabase() {
    abstract fun buildDao(): AppLockDao

    companion object {
        @Volatile
        private var INSTANCE: AppLockDatabase? = null

        fun getDatabase(context: Context): AppLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLockDatabase::class.java,
                    "applock_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AppLockRepository(private val dao: AppLockDao) {
    val lockedApps = dao.getAllLockedApps()
    suspend fun getApp(pkg: String) = dao.getAppByPackageName(pkg)
    suspend fun addApp(app: LockedApp) = dao.insert(app)
    suspend fun removeApp(app: LockedApp) = dao.delete(app)
}
