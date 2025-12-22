# Android Device Manager

An enterprise Android device management application for EMI-based device sales. This application enables shop owners to remotely manage customer devices through device owner privileges, implementing features like remote lock/unlock, location tracking, and EMI payment reminders.

## 🎯 Features

### Device Management

- **Remote Lock/Unlock** - Control device access via Firebase Cloud Messaging (FCM)
- **Real-time Location Tracking** - Monitor device location with 15-minute periodic updates
- **Device Owner Mode** - Full administrative control using Android Device Policy Manager
- **Factory Reset Protection (FRP)** - Secure devices with Google account binding

### Security

- **Automatic PIN Generation** - Secure 6-digit PIN creation during setup
- **Account Management** - Multi-account setup with locked modifications
- **Device Restrictions** - Comprehensive restrictions to prevent unauthorized changes
- **Kiosk Mode** - Lock screen overlay during device lock state

### Backend Integration

- **FCM Push Notifications** - Real-time device commands and EMI reminders
- **Device Registration** - Automatic backend registration with IMEI, PIN, and location
- **Heartbeat Monitoring** - Periodic device status updates (every 5 minutes)
- **Location Updates** - Automated location reporting to backend

## 🏗️ Architecture

### Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Async:** Kotlin Coroutines + Flow
- **Networking:** Retrofit + OkHttp
- **Local Storage:** Room Database + DataStore Preferences
- **Background Work:** WorkManager
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Location:** Google Play Services Location API

### Key Components

```
app/
├── data/
│   ├── local/          # Room DB, PreferencesManager
│   ├── model/          # Data models
│   ├── remote/         # API service, NetworkModule
│   └── repository/     # DeviceRepository
├── manager/            # DevicePolicyManagerHelper
├── service/            # Background services (FCM, monitoring, workers)
├── ui/
│   ├── lock/          # Lock screen UI
│   └── setup/         # Initial setup flow
└── EMIDeviceManagerApp.kt
```

## 📋 Prerequisites

### Development Environment

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11 or higher
- Android SDK 34 (API level 34)
- Gradle 8.0+

### Firebase Project

- Firebase project with **billing enabled** (required for FCM)
- `google-services.json` configured (see [FIREBASE_SETUP.md](FIREBASE_SETUP.md))
- Firebase Cloud Messaging enabled

### Backend API

- Backend server implementing the Customer Device API
- HTTPS endpoint for device registration and commands
- (API documentation: `CUSTOMER_DEVICE_API_DOCUMENTATION.md` - internal)

## 🚀 Setup Instructions

### 1. Clone the Repository

```bash
git clone <repository-url>
cd AndroidManager
```

### 2. Configure Firebase

Follow the detailed instructions in [FIREBASE_SETUP.md](FIREBASE_SETUP.md):

- Create Firebase project with billing enabled
- Add Android app to Firebase
- Download `google-services.json`
- Configure service account for backend integration

### 3. Configure Backend URL

Update `Constants.kt` with your backend details:

```kotlin
object Constants {
    const val BACKEND_URL = "https://your-backend.com"
    const val SHOP_ID = "your-shop-id"
    const val SHOP_NAME = "Your Shop Name"
}
```

### 4. Build and Install

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## 📱 Device Provisioning

### QR Code Provisioning (Recommended)

1. Factory reset the device
2. On "Welcome" screen, tap 6 times on the same spot
3. Scan QR code with provisioning data:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.androidmanager/.receiver.EMIDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "<APK_URL>",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true
}
```

### Manual Provisioning (Testing)

```bash
adb shell dpm set-device-owner com.androidmanager/.receiver.EMIDeviceAdminReceiver
```

### Setup Flow

After provisioning, the app will:

1. Generate random 6-digit PIN
2. Apply device restrictions
3. Prompt for shop owner's Google account (FRP)
4. Option to add customer's personal accounts (up to 2 more)
5. Lock all account modifications
6. Register device with backend
7. Start monitoring services

## 🔧 Configuration

### App Constants

Edit `app/src/main/java/com/androidmanager/util/Constants.kt`:

- `BACKEND_URL` - Your backend API endpoint
- `SHOP_ID` - Unique shop identifier
- `SHOP_NAME` - Display name for shop

### Permissions

Required permissions are auto-granted in Device Owner mode:

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - Location tracking
- `POST_NOTIFICATIONS` - EMI reminders (Android 13+)
- `INTERNET` / `ACCESS_NETWORK_STATE` - Backend communication

## 🔐 Security Features

### Factory Reset Protection (FRP)

- Shop owner's Google account is set as device admin during setup
- Even if removed from settings, the account is required after factory reset
- Prevents unauthorized device resets

### Account Locking

- After setup, `DISALLOW_MODIFY_ACCOUNTS` restriction is applied
- No accounts can be added or removed by end user
- Shop owner account and optional customer accounts are locked in place

### Device Restrictions

Applied during setup:

- `DISALLOW_FACTORY_RESET` - Prevent factory reset
- `DISALLOW_ADD_USER` / `DISALLOW_REMOVE_USER` - User management
- `DISALLOW_INSTALL_UNKNOWN_SOURCES` - Block sideloading
- `DISALLOW_USB_FILE_TRANSFER` - Prevent data theft
- And more... (see `DevicePolicyManagerHelper.kt`)

## 📡 Backend Integration

### Device Registration

On first setup:

```http
PUT /api/customer/device/fcm-token
Content-Type: application/json

{
  "fcmToken": "<token>",
  "imei1": "<device-imei>",
  "devicePin": "123456",
  "latitude": 28.7041,
  "longitude": 77.1025
}
```

### FCM Commands

Backend sends commands via FCM:

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

**EMI Reminder:**

```json
{
  "type": "EMI_REMINDER",
  "action": "SHOW_NOTIFICATION",
  "message": "Your EMI is due",
  "pendingAmount": "5000",
  "pendingEmisCount": "2"
}
```

### Automated Tasks

- **Location Updates:** Every 15 minutes via WorkManager
- **Heartbeat:** Every 5 minutes via DeviceMonitorService
- **Lock Status Reports:** Sent after lock/unlock operations

## 🐛 Troubleshooting

### FCM Token Not Generating

**Error:** `Firebase Installations Service is unavailable`

**Solution:** Enable billing on Firebase project:

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Navigate to Billing → Link a billing account

### Device Owner Provisioning Failed

**Error:** `Not allowed to set the device owner`

**Causes:**

- Device has Google accounts already added
- Device is not factory reset
- Work profile exists

**Solution:** Factory reset and provision before adding any accounts

### Location Not Updating

Check:

- Location permissions granted
- Google Play Services installed and updated
- GPS enabled on device
- Device has internet connectivity

## 📝 Development Notes

### Testing Without Device Owner

Some features (restrictions, account locking) require Device Owner mode. For testing:

1. Use Android Emulator with Google Play
2. Factory reset emulator
3. Use `adb shell dpm set-device-owner` command

### FCM Message Deduplication

The app ignores duplicate FCM messages received within 5 seconds to prevent UI freezes during message floods (e.g., after device reconnects to network).

### Build Variants

- **Debug:** Includes logging, allows USB debugging
- **Release:** Optimized build, minimal logging (configure in `build.gradle.kts`)

## 📄 License

[Add your license here]

## 🤝 Contributing

[Add contribution guidelines if applicable]

## 📞 Support

[Add support contact information]

---

**⚠️ Important:** This application uses Device Owner mode which has significant system-level control. Only deploy to devices you own/manage. Ensure compliance with local laws and regulations regarding device monitoring and user privacy.
