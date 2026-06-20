package com.example.applock.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.example.applock.AuthenticationActivity
import com.example.applock.data.AppLockDatabase
import com.example.applock.data.AppLockRepository
import kotlinx.coroutines.*

class AppLockService : Service() {

    companion object {
        val unlockedStates = HashSet<String>()
        val packageBackgroundTimes = HashMap<String, Long>()

        @Volatile
        var currentlyAuthenticatingPackage: String? = null

        fun markAsUnlocked(packageName: String) {
            synchronized(unlockedStates) {
                unlockedStates.add(packageName)
            }
            synchronized(packageBackgroundTimes) {
                packageBackgroundTimes.remove(packageName)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppLockRepository
    private var lastForegroundPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                synchronized(unlockedStates) { unlockedStates.clear() }
                synchronized(packageBackgroundTimes) { packageBackgroundTimes.clear() }
                lastForegroundPackage = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppLockRepository(AppLockDatabase.getDatabase(this).buildDao())
        
        val channel = NotificationChannel("app_lock", "AppLock Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(1, Notification.Builder(this, "app_lock").setContentTitle("AppLock Running").build())

        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        startTracking()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking() {
        serviceScope.launch {
            while (isActive) {
                delay(300)
                if (AppLockTileService.isPaused) continue

                val currentForeground = getForegroundPackage() ?: continue

                if (currentForeground != lastForegroundPackage) {
                    // We left the previous app
                    if (lastForegroundPackage != null && 
                        lastForegroundPackage != "com.example.applock" && 
                        !isLauncherOrSystem(lastForegroundPackage!!)) {
                        
                        synchronized(packageBackgroundTimes) {
                            packageBackgroundTimes[lastForegroundPackage!!] = System.currentTimeMillis()
                        }
                    }

                    lastForegroundPackage = currentForeground
                    checkApp(currentForeground)
                }
            }
        }
    }

    private suspend fun checkApp(packageName: String) {
        if (packageName == "com.example.applock" || isLauncherOrSystem(packageName)) return

        val app = repository.getApp(packageName) ?: return
        
        // Active Profile filtering could happen here.

        val bgTime = synchronized(packageBackgroundTimes) { packageBackgroundTimes[packageName] }
        val isCurrentlyUnlocked = synchronized(unlockedStates) { unlockedStates.contains(packageName) }

        var shouldLock = true
        if (isCurrentlyUnlocked) {
            if (bgTime == null) {
                shouldLock = false
            } else {
                val elapsed = System.currentTimeMillis() - bgTime
                val limit = when (app.reLockOption) {
                    "Immediately" -> 1000L // Small bounce tolerance
                    "After 1 minute" -> 60000L
                    "Re-lock on screen off" -> Long.MAX_VALUE
                    else -> 1000L
                }
                if (elapsed < limit) {
                    shouldLock = false
                } else {
                    // Grace period expired
                    synchronized(unlockedStates) { unlockedStates.remove(packageName) }
                    synchronized(packageBackgroundTimes) { packageBackgroundTimes.remove(packageName) }
                }
            }
        }

        if (shouldLock && currentlyAuthenticatingPackage != packageName) {
            currentlyAuthenticatingPackage = packageName
            val intent = Intent(this, AuthenticationActivity::class.java).apply {
                putExtra("target_package", packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }
    }

    private fun getForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 10000, time)
        val event = UsageEvents.Event()
        var currentPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentPkg = event.packageName
            }
        }
        return currentPkg
    }

    private fun isLauncherOrSystem(pkg: String): Boolean {
        return pkg.contains("launcher") || pkg == "com.android.systemui"
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
    }
}
