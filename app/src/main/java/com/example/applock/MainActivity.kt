package com.example.applock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.applock.service.AppLockService
import com.example.applock.ui.theme.AppLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start the service
        startService(Intent(this, AppLockService::class.java))

        setContent {
            AppLockTheme {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "AppLock Config",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            // One UI 8.5 squircle shapes and glassmorphism effect
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Usage Stats Permission", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { 
                                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) 
                                }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
