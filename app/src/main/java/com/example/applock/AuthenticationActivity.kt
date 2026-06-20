package com.example.applock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.applock.service.AppLockService
import com.example.applock.service.AppLockTileService
import com.example.applock.ui.theme.AppLockTheme

class AuthenticationActivity : ComponentActivity() {
    private var targetPackage: String? = null
    private var actionState: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fast_fade_in, R.anim.fast_fade_out)
        } else {
            overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out)
        }

        targetPackage = intent.getStringExtra("target_package")
        actionState = intent.getStringExtra("action")

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onAuthFailure()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (actionState == "action_toggle_pause") "Pause AppLock" else "App is Locked")
            .setSubtitle("Confirm your identity")
            .setDeviceCredentialAllowed(true)
            .build()
        
        biometricPrompt.authenticate(promptInfo)

        setContent {
            AppLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f) // Glassmorphism backdrop
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Locked", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    private fun onAuthSuccess() {
        if (actionState == "action_toggle_pause") {
            AppLockTileService.isPaused = true
        } else if (targetPackage != null) {
            AppLockService.markAsUnlocked(targetPackage!!)
        }
        AppLockService.currentlyAuthenticatingPackage = null
        finish()
    }

    private fun onAuthFailure() {
        AppLockService.currentlyAuthenticatingPackage = null
        if (actionState != "action_toggle_pause") goHome()
        finish()
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.fast_fade_in, R.anim.fast_fade_out)
        } else {
            overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out)
        }
    }
}
