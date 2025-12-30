# Building Production APK for AMAPI QR Provisioning

## Step 1: Build Signed Release APK

### Option A: Using Existing Keystore (if you have one)

```bash
# Build signed release APK
./gradlew assembleRelease
```

The APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

### Option B: Generate New Keystore First

If you don't have a keystore, create one:

```bash
# Generate keystore
keytool -genkey -v -keystore android-manager.jks \
  -alias android-manager-key \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass your_password_here \
  -keypass your_password_here
```

**Fill in the prompts:**
- Name: Your Name
- Organization: Your Company
- City: Your City
- State: Your State
- Country Code: US (or your country)

**Then configure in `app/build.gradle.kts`:**

Add this inside the `android` block:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../android-manager.jks")
        storePassword = "your_password_here"
        keyAlias = "android-manager-key"
        keyPassword = "your_password_here"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

---

## Step 2: Get APK Signature Hash

```bash
# Extract SHA-256 fingerprint from APK
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk | grep SHA256
```

**Example output:**
```
SHA256: 14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B6:3E:66:00:C8
```

**Remove colons and save this hash:**
```
146de983c5730650d8eeb9952f34fc6416a08342e61dbea88a0496b63e6600c8
```

---

## Step 3: Host APK Publicly

### Option A: Quick Test - GitHub Releases (Recommended)

1. **Create a GitHub repository** (can be private)

2. **Create a release:**
   ```bash
   # Tag and push
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. **Upload APK to release:**
   - Go to GitHub → Releases → Create new release
   - Upload `app-release.apk`
   - Publish release

4. **Get direct download URL:**
   ```
   https://github.com/YOUR_USERNAME/YOUR_REPO/releases/download/v1.0.0/app-release.apk
   ```

### Option B: Google Drive

1. Upload APK to Google Drive
2. Share → Anyone with link can view
3. Get shareable link
4. Convert to direct download:
   - Original: `https://drive.google.com/file/d/FILE_ID/view`
   - Direct: `https://drive.google.com/uc?export=download&id=FILE_ID`

### Option C: Dropbox

1. Upload APK to Dropbox
2. Share → Create link
3. Change `?dl=0` to `?dl=1` for direct download

### Option D: Firebase Hosting (Production)

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# Initialize hosting
firebase init hosting

# Copy APK to public folder
mkdir public
cp app/build/outputs/apk/release/app-release.apk public/

# Deploy
firebase deploy --only hosting

# Your URL will be:
# https://your-project.web.app/app-release.apk
```

---

## Step 4: Update Backend Configuration

Update your backend `.env` file:

```env
# AMAPI Configuration
ANDROID_MANAGEMENT_ENABLED=true
ANDROID_MANAGEMENT_ENTERPRISE_ID=enterprises/LC...
ANDROID_MANAGEMENT_DEFAULT_POLICY_ID=policy_emi_default
ANDROID_MANAGEMENT_SERVICE_ACCOUNT_PATH=./config/android-manager-service-account.json
AMAPI_WEBHOOK_SECRET=your_webhook_secret
BACKEND_URL=https://emi-backend-2wts.onrender.com

# APK Configuration (NEW!)
APP_DOWNLOAD_URL=https://YOUR_HOSTED_URL/app-release.apk
APP_SIGNATURE_CHECKSUM=146de983c5730650d8eeb9952f34fc6416a08342e61dbea88a0496b63e6600c8
APP_PACKAGE_NAME=com.androidmanager
```

---

## Step 5: Update Backend QR Generation

In `backend/services/androidManagementService.js`:

```javascript
/**
 * Build provisioning payload for QR code
 */
function buildProvisioningPayload(customerId, enrollmentToken) {
    const payload = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": 
            `${process.env.APP_PACKAGE_NAME}/.receiver.EMIDeviceAdminReceiver`,
        
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": 
            process.env.APP_SIGNATURE_CHECKSUM,
        
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": 
            process.env.APP_DOWNLOAD_URL,  // ← Use hosted URL
        
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": false,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
        
        "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
            "backend_url": process.env.BACKEND_URL,
            "enrollment_token": enrollmentToken,
            "customer_id": customerId,
            "enterprise_id": process.env.ANDROID_MANAGEMENT_ENTERPRISE_ID
        }
    };
    
    return payload;
}
```

---

## Step 6: Test the Complete Flow

### 6.1 Generate QR Code

```bash
curl -X POST https://emi-backend-2wts.onrender.com/api/admin/amapi/qr/CUSTOMER_ID \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"policyId": "policy_emi_default"}'
```

### 6.2 Verify QR Payload

Check the response includes:
- ✅ Correct download URL
- ✅ Correct signature checksum
- ✅ Correct package name

### 6.3 Test APK Download

```bash
# Test if APK downloads successfully
curl -o test.apk YOUR_APP_DOWNLOAD_URL

# Verify it's valid
file test.apk
# Should show: "Android package (APK)"
```

### 6.4 Factory Reset and Scan

1. Factory reset your test device
2. During setup wizard, connect to WiFi
3. Look for "Set up for work" or scan option
4. Scan the QR code
5. Device should download and install app automatically

---

## Troubleshooting

### APK Won't Download
- Check URL is publicly accessible
- Must be HTTPS for production
- Check server allows direct downloads

### Signature Mismatch
- Verify checksum matches exactly
- No colons or spaces in checksum
- Must use SHA-256 (not SHA-1)

### App Won't Install
- Check minimum SDK version (Android 10+)
- Verify APK is signed
- Check device has internet during setup

---

## Quick Build Script

Save as `build-release.sh`:

```bash
#!/bin/bash

echo "🔨 Building Release APK..."
./gradlew clean assembleRelease

echo ""
echo "✅ APK built successfully!"
echo "📍 Location: app/build/outputs/apk/release/app-release.apk"
echo ""

echo "🔐 Getting signature hash..."
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk | grep SHA256

echo ""
echo "📝 Next steps:"
echo "1. Remove colons from SHA256 hash"
echo "2. Upload APK to hosting (GitHub/Drive/Firebase)"
echo "3. Update backend .env with:"
echo "   - APP_DOWNLOAD_URL"
echo "   - APP_SIGNATURE_CHECKSUM"
echo "4. Generate new QR code"
echo "5. Factory reset device and scan QR"
```

Make executable:
```bash
chmod +x build-release.sh
./build-release.sh
```

---

## Production Checklist

- [ ] Release APK built and signed
- [ ] SHA-256 signature extracted
- [ ] APK uploaded to public hosting
- [ ] Backend .env updated with download URL
- [ ] Backend .env updated with signature checksum
- [ ] QR code generated and tested
- [ ] QR payload verified (correct URL, checksum)
- [ ] Test device factory reset
- [ ] QR scan successful
- [ ] App installed automatically
- [ ] AMAPI provisioning data extracted
- [ ] Device registered with backend

---

**Ready to build? Start with Step 1!** 🚀
