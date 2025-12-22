package com.androidmanager.service

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.manager.DevicePolicyManagerHelper
import com.androidmanager.ui.theme.AndroidManagerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lock Screen Activity - Displayed when device is locked due to payment issues
 * This screen cannot be bypassed by the user
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var policyHelper: DevicePolicyManagerHelper
    
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        policyHelper = DevicePolicyManagerHelper(this)

        // Make this a full-screen lock activity
        setupLockScreenWindow()

        // Start lock task mode if device owner
        if (policyHelper.isDeviceOwner()) {
            startLockTask()
        }

        // Register unlock receiver
        registerUnlockReceiver()

        setContent {
            AndroidManagerTheme {
                LockScreen()
            }
        }
    }

    private fun setupLockScreenWindow() {
        // Fullscreen, no status bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Show on lock screen
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Prevent screenshots
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun registerUnlockReceiver() {
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.androidmanager.ACTION_DEVICE_UNLOCKED") {
                    finishLockScreen()
                }
            }
        }

        val filter = IntentFilter("com.androidmanager.ACTION_DEVICE_UNLOCKED")
        registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    @Composable
    private fun LockScreen() {
        var lockMessage by remember { mutableStateOf("") }
        var shopName by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            lockMessage = preferencesManager.getLockMessage() 
                ?: "Payment overdue. Please contact the shop owner to unlock your device."
            shopName = preferencesManager.shopId.first() ?: ""
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Lock icon
                Text(
                    text = "🔒",
                    fontSize = 80.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Title
                Text(
                    text = "Device Locked",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Lock message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF16213E)
                    )
                ) {
                    Text(
                        text = lockMessage,
                        color = Color(0xFFE94560),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Contact info
                Text(
                    text = "Please make your payment to unlock this device.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                if (shopName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Shop: $shopName",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Emergency call button (required by law in some regions)
                OutlinedButton(
                    onClick = { makeEmergencyCall() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Emergency Call")
                }
            }
        }
    }

    private fun makeEmergencyCall() {
        // Allow emergency calls (legally required in most jurisdictions)
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:112")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun finishLockScreen() {
        if (policyHelper.isDeviceOwner()) {
            stopLockTask()
        }
        finish()
    }

    // Prevent back button
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - prevent back navigation
    }

    // Prevent recent apps button from working
    override fun onPause() {
        super.onPause()
        
        lifecycleScope.launch {
            if (preferencesManager.isDeviceLockedSync()) {
                // Bring back to front if still locked
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.moveTaskToFront(taskId, 0)
            }
        }
    }

    // Block hardware keys
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        // If we lose focus while locked, try to regain it
        if (!hasFocus) {
            lifecycleScope.launch {
                if (preferencesManager.isDeviceLockedSync()) {
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    activityManager.moveTaskToFront(taskId, 0)
                }
            }
        }
    }
}
