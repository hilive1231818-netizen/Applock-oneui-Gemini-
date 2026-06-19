package com.example.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppLockDatabase
import com.example.data.AppLockRepository
import com.example.data.LockedApp
import com.example.service.AppLockService.Companion.unlockedPackages
import com.example.ui.AuthenticationActivity
import kotlinx.coroutines.*

class AppLockService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var repository: AppLockRepository

    private var activeMonitoringJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var homePackageName: String = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen off. Resetting all unlock timers.")
                synchronized(unlockedPackages) {
                    unlockedPackages.clear()
                }
            }
        }
    }

    companion object {
        private const val TAG = "AppLockService"
        private const val NOTIFICATION_ID = 8522
        private const val CHANNEL_ID = "app_lock_service_channel"

        // Holds in-memory record of unlocked packages with their last active and unlock timestamps
        // Map: PackageName -> (unlockTimeMillis)
        val unlockedPackages = HashMap<String, Long>()

        // Tracks which app is currently undergoing biometric authentication to avoid launching multiple prompts
        @Volatile
        var currentlyAuthenticatingPackage: String? = null

        /**
         * Helper to register package as successfully unlocked.
         */
        fun markAsUnlocked(packageName: String) {
            synchronized(unlockedPackages) {
                unlockedPackages[packageName] = System.currentTimeMillis()
                Log.d(TAG, "Package unlocked in-memory: $packageName at ${unlockedPackages[packageName]}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppLockService onCreate")
        
        try {
            createNotificationChannel()
            startForegroundServiceNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service notification: ${e.message}")
        }

        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val database = AppLockDatabase.getDatabase(applicationContext)
        repository = AppLockRepository(database.appLockDao())

        homePackageName = getHomePackageName()

        // Register receiver for screen off events
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)

        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AppLockService onStartCommand")
        try {
            startForegroundServiceNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand foreground: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppLockService onDestroy")
        unregisterReceiver(screenReceiver)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getHomePackageName(): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName ?: "com.sec.android.app.launcher"
    }

    private fun startForegroundServiceNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps App Lock secure background service running."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        activeMonitoringJob?.cancel()
        activeMonitoringJob = serviceScope.launch {
            while (isActive) {
                delay(500) // Poll foreground app state every 500ms
                try {
                    val currentForeground = getForegroundPackageName() ?: continue
                    if (currentForeground == packageName || currentForeground == homePackageName) {
                        // User is in our settings screen or on launcher home, update state but don't prompt
                        lastForegroundPackage = currentForeground
                        continue
                    }

                    if (currentForeground != lastForegroundPackage) {
                        Log.v(TAG, "App shift detected: $lastForegroundPackage -> $currentForeground")
                        checkAndLockPackage(currentForeground)
                    }

                    lastForegroundPackage = currentForeground
                } catch (e: Exception) {
                    Log.e(TAG, "Error in background usage tracking: ${e.message}")
                }
            }
        }
    }

    private suspend fun checkAndLockPackage(packageName: String) {
        val matchedApp = repository.getAppByPackageName(packageName)
        if (matchedApp != null && matchedApp.isLocked) {
            val isBypassed = shouldBypassLock(matchedApp)
            if (!isBypassed) {
                // Determine if we are already authenticating this application to avoid standard activity stacking
                if (currentlyAuthenticatingPackage != packageName) {
                    launchAuthOverlay(packageName)
                }
            }
        }
    }

    private fun shouldBypassLock(app: LockedApp): Boolean {
        synchronized(unlockedPackages) {
            val unlockTime = unlockedPackages[app.packageName] ?: return false
            val elapsed = System.currentTimeMillis() - unlockTime

            if (app.reLockOption.startsWith("After") && app.reLockOption.endsWith("minutes")) {
                try {
                    val minutesStr = app.reLockOption.removePrefix("After ").removeSuffix(" minutes").trim()
                    val minutes = minutesStr.toInt()
                    return elapsed < (minutes * 60 * 1000L)
                } catch (e: Exception) {
                    return elapsed < 2000
                }
            }

            return when (app.reLockOption) {
                "Immediately" -> {
                    // With "Immediately", once the user navigates away from the app (even momentarily), we require unlock.
                    // However, we allow a tolerance window of 3 seconds initially upon authentication back-to-back configuration
                    // to prevent loop-stacking due to delayed OS focus reports.
                    elapsed < 3000
                }
                "After 1 minute" -> {
                    elapsed < 60000
                }
                "Re-lock on screen off" -> {
                    // Stays unlocked in-memory until screen off event resets the maps
                    true
                }
                else -> elapsed < 2000 // immediate safe baseline
            }
        }
    }

    private fun launchAuthOverlay(targetPackageName: String) {
        Log.i(TAG, "Launching Auth Overlay Activity for secure application: $targetPackageName")
        currentlyAuthenticatingPackage = targetPackageName

        val authIntent = Intent(this, AuthenticationActivity::class.java).apply {
            putExtra(AuthenticationActivity.EXTRA_TARGET_PACKAGE, targetPackageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(authIntent)
    }

    /**
     * Standard implementation of queryEvents for PACKAGE_USAGE_STATS permission.
     */
    private fun getForegroundPackageName(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Last 10 seconds matches standard latency buffer
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastResumedEvent: UsageEvents.Event? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumedEvent = event
            }
        }

        return lastResumedEvent?.packageName
    }
}
