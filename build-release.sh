#!/bin/bash

echo "======================================"
echo "🔨 Building Android Manager Release APK"
echo "======================================"
echo ""

# Step 1: Clean and build
echo "📦 Step 1: Building release APK..."
./gradlew clean assembleRelease

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ BUILD SUCCESSFUL!"
    echo ""
    
    # Step 2: Show APK location
    echo "📍 APK Location:"
    echo "   app/build/outputs/apk/release/app-release.apk"
    echo ""
    
    # Step 3: Get signature
    echo "🔐 Step 2: Extracting SHA-256 signature..."
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    
    if [ -f "$APK_PATH" ]; then
        echo ""
        keytool -printcert -jarfile "$APK_PATH" | grep SHA256
        echo ""
        echo "⚠️  IMPORTANT: Copy the SHA256 hash WITHOUT colons"
        echo ""
        
        # Step 4: Show file size
        echo "📏 APK Size:"
        ls -lh "$APK_PATH" | awk '{print "   " $5}'
        echo ""
        
        # Step 5: Next steps
        echo "======================================"
        echo "📝 NEXT STEPS:"
        echo "======================================"
        echo ""
        echo "1️⃣  Remove colons from SHA256 hash above"
        echo "    Example: 14:6D:E9... → 146de9..."
        echo ""
        echo "2️⃣  Upload APK to public hosting:"
        echo "    • GitHub Releases (easiest)"
        echo "    • Google Drive (quick test)"
        echo "    • Firebase Hosting (production)"
        echo ""
        echo "3️⃣  Update backend .env file:"
        echo "    APP_DOWNLOAD_URL=https://your-url/app-release.apk"
        echo "    APP_SIGNATURE_CHECKSUM=<hash without colons>"
        echo ""
        echo "4️⃣  Generate new QR code from backend"
        echo ""
        echo "5️⃣  Factory reset device and scan QR"
        echo ""
        echo "======================================"
        echo "See BUILD_RELEASE_GUIDE.md for details"
        echo "======================================"
        
    else
        echo "❌ APK file not found at expected location"
    fi
else
    echo ""
    echo "❌ BUILD FAILED"
    echo "Check the error messages above"
fi
