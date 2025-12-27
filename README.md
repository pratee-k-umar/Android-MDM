# Android Manager - Enterprise Device Management

Enterprise Android device management app with **AMAPI (Android Management API)** integration for zero-touch provisioning, real-time location tracking, and remote device control.

---

## 🎯 **Key Features**

### AMAPI Integration
- ✅ **QR Code Provisioning** - Zero-touch device enrollment during factory reset
- ✅ **Enterprise Policy Management** - Centralized policy enforcement from backend
- ✅ **Automatic Device Owner Setup** - No manual adb commands required

### Device Management
- ✅ **Remote Lock/Unlock** - Control device access via Firebase Cloud Messaging
- ✅ **Real-time Location Tracking** - Smart throttling with 15-minute/50-meter thresholds
- ✅ **Factory Reset Protection (FRP)** - Secure devices with Google account binding
- ✅ **Device Owner Mode** - Full administrative control

### Backend Integration
- ✅ **Automated Registration** - Device auto-registers with backend on first boot
- ✅ **FCM Push Notifications** - Real-time commands and status updates
- ✅ **Location Reporting** - Periodic updates to backend with online status

---

## 🏗️ **Tech Stack**

| Component | Technology |
|-----------|------------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose |
| **Async** | Coroutines + Flow |
| **Networking** | Retrofit + OkHttp |
| **Storage** | DataStore Preferences |
| **Background Work** | WorkManager + Services |
| **Push Notifications** | Firebase Cloud Messaging |
| **Location** | Google Play Services Location |
| **Enterprise** | Android Management API (AMAPI) |

---

## 📋 **Prerequisites**

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** 11+
- **Android SDK** 34+
- **Firebase Project** with billing enabled
- **Backend** with AMAPI integration ([Backend Repo](https://github.com/your-backend-repo))

---

## 🚀 **Quick Start**

### 1. Clone & Setup

```bash
git clone <repo-url>
cd AndroidManager

# Add google-services.json to app/
# (Download from Firebase Console)
```

### 2. Configure Constants

**File:** `app/src/main/java/com/androidmanager/util/Constants.kt`

```kotlin
object Constants {
    const val BACKEND_URL = "https://your-backend.com"
    const val SHOP_ID = "your-shop-id"
    const val SHOP_NAME = "Your Shop Name"
}
```

### 3. Build Release APK

```bash
./gradlew clean assembleRelease
```

APK will be at: `app/build/outputs/apk/release/app-release.apk`

---

## 📱 **Provisioning Methods**

### Method 1: AMAPI QR Provisioning (Production)

**Backend generates QR code:**
```bash
POST /api/admin/amapi/qr/:customerId
```

**QR contains:**
- App download URL
- Enrollment token
- Backend URL
- Customer ID

**Provisioning flow:**
1. Factory reset device
2. Scan QR during setup wizard
3. Device downloads & installs app automatically
4. AMAPI applies policy (restrictions, location, etc.)
5. App registers with backend
6. Done! ✅

### Method 2: Manual (Testing)

```bash
# Install APK
adb install app-release.apk

# Set as Device Owner
adb shell dpm set-device-owner com.androidmanager/.receiver.EMIDeviceAdminReceiver
```

---

## 🔐 **Security & Privacy**

### Factory Reset Protection (FRP)
- Device requires shop owner's Google account after factory reset
- Prevents unauthorized device resets

### Device Restrictions (via AMAPI Policy)
- Factory reset disabled
- USB file transfer blocked
- Unknown sources installation blocked
- System settings locked
- Location tracking enforced

### Data Handling
- **Location:** Sent to backend every 15 min or when moved 50+ meters
- **IMEI:** Used for device identification
- **FCM Token:** For backend commands

---

## 📡 **Backend Integration**

### Device Registration

```http
PUT /api/customer/device/fcm-token
Content-Type: application/json

{
  "fcmToken": "<token>",
  "imei1": "<imei>",
  "latitude": 28.7041,
  "longitude": 77.1025
}
```

### FCM Commands

**Lock Device:**
```json
{
  "type": "DEVICE_LOCK_STATUS",
  "action": "LOCK_DEVICE"
}
```

**Unlock Device:**
```json
{
  "type": "DEVICE_LOCK_STATUS",
  "action": "UNLOCK_DEVICE"
}
```

---

## 📂 **Project Structure**

```
app/
├── data/
│   ├── local/          # PreferencesManager (AMAPI data storage)
│   ├── model/          # Data models
│   ├── remote/         # API service, NetworkModule
│   └── repository/     # DeviceRepository
├── manager/            # DevicePolicyManagerHelper
├── receiver/           # EMIDeviceAdminReceiver (AMAPI provisioning)
├── service/            
│   ├── DeviceMonitorService.kt
│   └── EMIFirebaseMessagingService.kt
├── ui/
│   ├── lock/          # Lock screen overlay
│   └── setup/         # SetupActivity (AMAPI data extraction)
└── EMIDeviceManagerApp.kt
```

---

## 🔧 **Configuration Files**

| File | Purpose | Commit to Git? |
|------|---------|----------------|
| `google-services.json` | Firebase config | ❌ No (in .gitignore) |
| `android-manager.jks` | Signing keystore | ❌ NEVER! |
| `Constants.kt` | Backend URL | ✅ Yes (or use BuildConfig) |

---

## 🐛 **Troubleshooting**

### FCM Token Not Generating

**Error:** `Firebase Installations Service unavailable`

**Fix:** Enable billing on Firebase project:
1. [Google Cloud Console](https://console.cloud.google.com/)
2. Select project → Billing → Link account

### AMAPI Provisioning Failed

**Error:** Device doesn't download app

**Check:**
- ✅ APK hosted publicly (GitHub Releases, Firebase Hosting)
- ✅ Backend `.env` has correct `APP_DOWNLOAD_URL`
- ✅ Backend `.env` has correct `APP_SIGNATURE_CHECKSUM` (SHA-256 without colons)
- ✅ Device has internet during setup

### Location Not Updating

**Check:**
- ✅ Device has GPS enabled
- ✅ Google Play Services installed
- ✅ AMAPI policy allows location access
- ✅ Device has internet connectivity

---

## 📖 **Additional Documentation**

- **[BUILD_RELEASE_GUIDE.md](BUILD_RELEASE_GUIDE.md)** - Complete APK build & hosting guide
- **[AMAPI_API_DOCUMENTATION.md](AMAPI_API_DOCUMENTATION.md)** - Backend AMAPI API reference
- **[FIREBASE_SETUP.md](FIREBASE_SETUP.md)** - Firebase project configuration

---

## 🔑 **Environment Variables (Backend)**

Required in backend `.env`:

```env
# AMAPI Configuration
ANDROID_MANAGEMENT_ENABLED=true
ANDROID_MANAGEMENT_ENTERPRISE_ID=enterprises/LC...
ANDROID_MANAGEMENT_DEFAULT_POLICY_ID=policy_emi_default
ANDROID_MANAGEMENT_SERVICE_ACCOUNT_PATH=./config/service-account.json
AMAPI_WEBHOOK_SECRET=your_secret

# APK Configuration
APP_DOWNLOAD_URL=https://your-hosting-url/app-release.apk
APP_SIGNATURE_CHECKSUM=your_sha256_without_colons
APP_PACKAGE_NAME=com.androidmanager
```

---

## 📊 **Key Metrics**

- **Min SDK:** 29 (Android 10)
- **Target SDK:** 36
- **APK Size:** ~3.5 MB
- **Location Update Frequency:** 15 min or 50m movement
- **FCM Response Time:** < 2 seconds

---

## ⚠️ **Important Notes**

- **Device Owner mode** grants significant system-level control
- Only deploy to devices you **own/manage**
- Ensure compliance with **local privacy laws**
- **Never commit** `google-services.json` or `*.jks` files to Git
- **QR provisioning** requires publicly accessible APK URL

---

**Built with for enterprise device management**
