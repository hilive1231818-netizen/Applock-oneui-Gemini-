package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LockedApp::class], version = 1, exportSchema = false)
abstract class AppLockDatabase : RoomDatabase() {
    abstract fun appLockDao(): AppLockDao

    companion object {
        @Volatile
        private var INSTANCE: AppLockDatabase? = null

        fun getDatabase(context: Context): AppLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLockDatabase::class.java,
                    "app_lock_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
