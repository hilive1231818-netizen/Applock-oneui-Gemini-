package com.example.data

import kotlinx.coroutines.flow.Flow

class AppLockRepository(private val appLockDao: AppLockDao) {
    val allAppsFlow: Flow<List<LockedApp>> = appLockDao.getAllAppsFlow()
    val lockedAppsFlow: Flow<List<LockedApp>> = appLockDao.getLockedAppsFlow()

    suspend fun getAppByPackageName(packageName: String): LockedApp? {
        return appLockDao.getAppByPackageName(packageName)
    }

    suspend fun getLockedAppsList(): List<LockedApp> {
        return appLockDao.getLockedAppsList()
    }

    suspend fun updateAppLockState(packageName: String, appName: String, isLocked: Boolean, reLockOption: String = "IMMEDIATE") {
        val app = LockedApp(
            packageName = packageName,
            appName = appName,
            isLocked = isLocked,
            reLockOption = reLockOption
        )
        appLockDao.insertOrUpdate(app)
    }

    suspend fun saveApp(app: LockedApp) {
        appLockDao.insertOrUpdate(app)
    }

    suspend fun deleteApp(app: LockedApp) {
        appLockDao.delete(app)
    }
}
