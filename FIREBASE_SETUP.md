# 🔐 Firebase Setup Instructions

This project requires Firebase Cloud Messaging (FCM) for device management. Follow these steps to set up your Firebase credentials.

## Prerequisites

- A Google account
- A Firebase project with **billing enabled** (required for FCM)

## Android App Setup

### 1. Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or use an existing one
3. **Important:** Ensure billing is enabled (Settings → Usage and billing)

### 2. Add Android App to Firebase

1. In your Firebase project, click "Add app" → Android
2. Enter package name: `com.androidmanager`
3. Download `google-services.json`

### 3. Configure the App

1. Copy the downloaded `google-services.json` to `app/google-services.json`
2. **Never commit this file to Git** (it's already in `.gitignore`)

### 4. Verify Setup

Build the app:

```bash
./gradlew assembleDebug
```

## File Structure

```
app/
├── google-services.json            # ⛔ Your actual config (gitignored)
└── google-services.json.sample     # ✅ Template (committed to Git)
```

## Security Notes

- ✅ `google-services.json` is protected by `.gitignore`
- ✅ Never commit keystores or API keys
- ✅ Use separate Firebase projects for dev/staging/production

---

## Backend Setup: Service Account

Your backend needs a Firebase service account to send FCM messages to devices.

### Generate Service Account Key

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click the **gear icon** ⚙️ → **Project settings**
4. Navigate to **Service accounts** tab
5. Click **Generate new private key**
6. Confirm by clicking **Generate key**
7. Save the JSON file securely on your backend server

> **⚠️ IMPORTANT:** Never commit this JSON file to Git!

### Secure the File

```bash
# Store securely on backend server
mv firebase-service-account.json /etc/secrets/
chmod 600 /etc/secrets/firebase-service-account.json
```

### Use in Backend

**Node.js:**
```javascript
const admin = require('firebase-admin');
const serviceAccount = require('/etc/secrets/firebase-service-account.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});
```

**Python:**
```python
import firebase_admin
from firebase_admin import credentials

cred = credentials.Certificate('/etc/secrets/firebase-service-account.json')
firebase_admin.initialize_app(cred)
```

---

## Troubleshooting

### FCM Token Not Generating

**Error:** `Firebase Installations Service is unavailable`

**Solution:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Go to Billing → Link a billing account
4. Rebuild the app

### Build Fails: "google-services.json missing"

Copy `google-services.json.sample` to `google-services.json` and replace with your actual Firebase config.

### Service Account Permission Denied

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → IAM & Admin
2. Find your service account email
3. Add role: **Firebase Admin SDK Administrator Service Agent**

---

## Additional Resources

- [Firebase Android Setup](https://firebase.google.com/docs/android/setup)
- [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
- [FCM Documentation](https://firebase.google.com/docs/cloud-messaging)
