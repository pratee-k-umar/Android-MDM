# Android Manager - Deploy to Emulator Script
# This script builds, installs, and configures the app on an emulator

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Android Manager - Emulator Deployment" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Function to run command and check for errors
function Invoke-CommandWithCheck {
    param(
        [string]$Command,
        [string]$Description
    )
    
    Write-Host "`n[STEP] $Description" -ForegroundColor Yellow
    Write-Host "Command: $Command" -ForegroundColor Gray
    
    # Execute command and capture output
    $output = Invoke-Expression $Command 2>&1
    $exitCode = $LASTEXITCODE
    
    # Display output
    if ($output) {
        Write-Host $output
    }
    
    # Check for errors
    if ($exitCode -ne 0 -or $output -match "FAILED|Error|Exception") {
        Write-Host "`n❌ FAILED: $Description" -ForegroundColor Red
        Write-Host "Command: $Command" -ForegroundColor Red
        Write-Host "Exit Code: $exitCode" -ForegroundColor Red
        if ($output) {
            Write-Host "Output:" -ForegroundColor Red
            Write-Host $output -ForegroundColor Red
        }
        Write-Host "`n========================================" -ForegroundColor Red
        Write-Host "Deployment stopped due to error" -ForegroundColor Red
        Write-Host "========================================`n" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "✅ Success: $Description" -ForegroundColor Green
}

# Step 1: Build APK
Invoke-CommandWithCheck -Command "./gradlew assembleDebug" -Description "Building debug APK"

# Step 2: Install APK
Invoke-CommandWithCheck -Command "adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk" -Description "Installing APK to emulator"

# Step 3: Set Device Owner
Invoke-CommandWithCheck -Command "adb -s emulator-5554 shell dpm set-device-owner com.androidmanager/.receiver.EMIDeviceAdminReceiver" -Description "Setting device owner"

# Step 4: Grant Permissions
Invoke-CommandWithCheck -Command "adb shell pm grant com.androidmanager android.permission.ACCESS_FINE_LOCATION" -Description "Granting ACCESS_FINE_LOCATION"

Invoke-CommandWithCheck -Command "adb shell pm grant com.androidmanager android.permission.ACCESS_COARSE_LOCATION" -Description "Granting ACCESS_COARSE_LOCATION"

Invoke-CommandWithCheck -Command "adb shell pm grant com.androidmanager android.permission.ACCESS_BACKGROUND_LOCATION" -Description "Granting ACCESS_BACKGROUND_LOCATION"

Invoke-CommandWithCheck -Command "adb shell pm grant com.androidmanager android.permission.POST_NOTIFICATIONS" -Description "Granting POST_NOTIFICATIONS"

Invoke-CommandWithCheck -Command "adb shell pm grant com.androidmanager android.permission.READ_PHONE_STATE" -Description "Granting READ_PHONE_STATE"

# Step 5: Launch App
Invoke-CommandWithCheck -Command "adb -s emulator-5554 shell am start -n com.androidmanager/.MainActivity" -Description "Launching MainActivity"

# Success
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "✅ Deployment completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

Write-Host "`nUseful commands:" -ForegroundColor Cyan
Write-Host "  View logs: adb logcat -s SetupActivity DeviceMonitorService EMIFirebaseMessagingService" -ForegroundColor Gray
Write-Host "  Clear app data: adb shell pm clear com.androidmanager" -ForegroundColor Gray
Write-Host "  Uninstall: adb uninstall com.androidmanager`n" -ForegroundColor Gray
