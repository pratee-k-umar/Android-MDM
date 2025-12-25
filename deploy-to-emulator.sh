#!/bin/bash
# Android Manager - Deploy to Emulator Script
# This script builds, installs, and configures the app on an emulator

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m' # No Color

# Function to run command and check for errors
run_command() {
    local description="$1"
    local command="$2"
    
    echo -e "\n${YELLOW}[STEP] ${description}${NC}"
    echo -e "${GRAY}Command: ${command}${NC}"
    
    # Execute command and capture output
    output=$(eval "$command" 2>&1)
    exit_code=$?
    
    # Display output
    if [ -n "$output" ]; then
        echo "$output"
    fi
    
    # Check for errors
    if [ $exit_code -ne 0 ] || echo "$output" | grep -iq "FAILED\|Error\|Exception"; then
        echo -e "\n${RED}❌ FAILED: ${description}${NC}"
        echo -e "${RED}Command: ${command}${NC}"
        echo -e "${RED}Exit Code: ${exit_code}${NC}"
        if [ -n "$output" ]; then
            echo -e "${RED}Output:${NC}"
            echo -e "${RED}${output}${NC}"
        fi
        echo -e "\n${RED}========================================${NC}"
        echo -e "${RED}Deployment stopped due to error${NC}"
        echo -e "${RED}========================================${NC}\n"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Success: ${description}${NC}"
}

# Header
echo -e "\n${CYAN}========================================${NC}"
echo -e "${CYAN}Android Manager - Emulator Deployment${NC}"
echo -e "${CYAN}========================================${NC}\n"

# Step 1: Build APK
run_command "Building debug APK" "./gradlew assembleDebug"

# Step 2: Install APK
run_command "Installing APK to emulator" "adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk"

# Step 3: Set Device Owner
run_command "Setting device owner" "adb -s emulator-5554 shell dpm set-device-owner com.androidmanager/.receiver.EMIDeviceAdminReceiver"

# Step 4: Grant Permissions
run_command "Granting ACCESS_FINE_LOCATION" "adb shell pm grant com.androidmanager android.permission.ACCESS_FINE_LOCATION"

run_command "Granting ACCESS_COARSE_LOCATION" "adb shell pm grant com.androidmanager android.permission.ACCESS_COARSE_LOCATION"

run_command "Granting ACCESS_BACKGROUND_LOCATION" "adb shell pm grant com.androidmanager android.permission.ACCESS_BACKGROUND_LOCATION"

run_command "Granting POST_NOTIFICATIONS" "adb shell pm grant com.androidmanager android.permission.POST_NOTIFICATIONS"

run_command "Granting READ_PHONE_STATE" "adb shell pm grant com.androidmanager android.permission.READ_PHONE_STATE"

# Step 5: Launch App
run_command "Launching MainActivity" "adb -s emulator-5554 shell am start -n com.androidmanager/.MainActivity"

# Success
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"

echo -e "\n${CYAN}Useful commands:${NC}"
echo -e "${GRAY}  View logs: adb logcat -s SetupActivity DeviceMonitorService EMIFirebaseMessagingService${NC}"
echo -e "${GRAY}  Clear app data: adb shell pm clear com.androidmanager${NC}"
echo -e "${GRAY}  Uninstall: adb uninstall com.androidmanager${NC}\n"
