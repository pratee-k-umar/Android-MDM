package com.androidmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.androidmanager.service.DeviceMonitorService
import com.androidmanager.service.LockScreenActivity
import com.androidmanager.ui.setup.SetupActivity
import com.androidmanager.ui.theme.AndroidManagerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main Activity - Entry point for the app
 * Handles navigation to setup or admin panel based on state
 */
class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var policyHelper: DevicePolicyManagerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)
        policyHelper = DevicePolicyManagerHelper(this)

        // Check if this is AMAPI provisioning and extract data
        checkAndExtractAmapiProvisioning()

        lifecycleScope.launch {
            val isSetupComplete = preferencesManager.isSetupComplete.first()
            val isLocked = preferencesManager.isDeviceLockedSync()

            when {
                // If device is locked, show lock screen
                isLocked -> {
                    startActivity(Intent(this@MainActivity, LockScreenActivity::class.java))
                    finish()
                }
                // If setup not complete, go to setup
                !isSetupComplete -> {
                    startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                    finish()
                }
                // Setup complete - just ensure service is running and exit
                else -> {
                    try {
                        startForegroundService(Intent(this@MainActivity, DeviceMonitorService::class.java))
                    } catch (e: Exception) {
                        // Ignore - service will start on boot
                    }
                    // Close app - customer shouldn't see anything
                    finish()
                }
            }
        }
    }

    /**
     * Check if activity was launched after AMAPI provisioning
     * and extract enrollment data from intent extras
     */
    private fun checkAndExtractAmapiProvisioning() {
        val action = intent.action
        android.util.Log.d("MainActivity", "Launched with action: $action")
        
        if (action == "android.app.action.PROVISIONING_SUCCESSFUL" ||
            action == "android.app.action.ADMIN_POLICY_COMPLIANCE") {
            
            android.util.Log.d("MainActivity", "✅ AMAPI provisioning detected!")
            
            // Extract the admin extras bundle
            val adminExtras = intent.getBundleExtra("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE")
            
            if (adminExtras != null) {
                lifecycleScope.launch {
                    try {
                        // Extract AMAPI data
                        val backendUrl = adminExtras.getString("backend_url")
                        val enrollmentToken = adminExtras.getString("enrollment_token")
                        val customerId = adminExtras.getString("customer_id")
                        val enterpriseId = adminExtras.getString("enterprise_id")
                        
                        android.util.Log.d("MainActivity", "AMAPI Provisioning Data:")
                        android.util.Log.d("MainActivity", "  Backend URL: $backendUrl")
                        android.util.Log.d("MainActivity", "  Customer ID: $customerId")
                        android.util.Log.d("MainActivity", "  Enterprise ID: $enterpriseId")
                        android.util.Log.d("MainActivity", "  Enrollment Token: ${enrollmentToken?.take(20)}...")
                        
                        // Store in preferences
                        backendUrl?.let { preferencesManager.setBackendUrl(it) }
                        customerId?.let { preferencesManager.setCustomerId(it) }
                        enrollmentToken?.let { preferencesManager.setEnrollmentToken(it) }
                        enterpriseId?.let { preferencesManager.setEnterpriseId(it) }
                        preferencesManager.setAmapiProvisioned(true)
                        
                        android.util.Log.d("MainActivity", "✅ AMAPI data stored successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error extracting AMAPI data", e)
                    }
                }
            } else {
                android.util.Log.w("MainActivity", "⚠️ No admin extras bundle found in provisioning intent")
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AdminPanel() {
        var deviceId by remember { mutableStateOf("") }
        var isDeviceOwner by remember { mutableStateOf(false) }
        var lockedAccount by remember { mutableStateOf<String?>(null) }
        var isDeviceLocked by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            deviceId = preferencesManager.getDeviceIdSync() ?: "Unknown"
            isDeviceOwner = policyHelper.isDeviceOwner()
            lockedAccount = preferencesManager.getLockedAccountEmail()
            isDeviceLocked = preferencesManager.isDeviceLockedSync()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("EMI Device Manager") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDeviceOwner) 
                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else 
                            Color(0xFFF44336).copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Device Status",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        StatusRow("Device Owner", if (isDeviceOwner) "Active ✓" else "Inactive ✗")
                        StatusRow("Device ID", deviceId.take(8) + "...")
                        StatusRow("Protected Account", lockedAccount ?: "None")
                        StatusRow("Lock Status", if (isDeviceLocked) "LOCKED 🔒" else "Unlocked 🔓")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Quick Actions
                Text(
                    text = "Admin Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Test lock button (for testing)
                OutlinedButton(
                    onClick = {
                        lifecycleScope.launch {
                            preferencesManager.setDeviceLocked(true, "Test lock - Payment overdue")
                            startActivity(Intent(this@MainActivity, LockScreenActivity::class.java))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test Lock Screen")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Unlock button (for testing)
                OutlinedButton(
                    onClick = {
                        lifecycleScope.launch {
                            preferencesManager.setDeviceLocked(false)
                            isDeviceLocked = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock Device")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Refresh restrictions
                if (isDeviceOwner) {
                    OutlinedButton(
                        onClick = {
                            policyHelper.applyDeviceRestrictions()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Restrictions")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Info text
                Text(
                    text = "This panel is for shop owner administration only.\n" +
                            "The app will be hidden from the customer.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    private fun StatusRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.Gray
            )
            Text(
                text = value,
                fontWeight = FontWeight.Medium
            )
        }
    }
}