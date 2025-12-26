# PowerShell script to build Android Manager release APK

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "🔨 Building Android Manager Release APK" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build
Write-Host "📦 Step 1: Building release APK..." -ForegroundColor Yellow
& .\gradlew.bat clean assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host ""
    
    # Step 2: Show location
    $apkPath = "app\build\outputs\apk\release\app-release.apk"
    Write-Host "📍 APK Location:" -ForegroundColor Yellow
    Write-Host "   $apkPath" -ForegroundColor White
    Write-Host ""
    
    # Step 3: Get signature
    if (Test-Path $apkPath) {
        Write-Host "🔐 Step 2: Extracting SHA-256 signature..." -ForegroundColor Yellow
        Write-Host ""
        
        keytool -printcert -jarfile $apkPath | Select-String "SHA256"
        
        Write-Host ""
        Write-Host "⚠️  IMPORTANT: Copy the SHA256 hash WITHOUT colons" -ForegroundColor Red
        Write-Host ""
        
        # Step 4: File size
        $size = (Get-Item $apkPath).Length / 1MB
        Write-Host "📏 APK Size:" -ForegroundColor Yellow
        Write-Host ("   {0:N2} MB" -f $size) -ForegroundColor White
        Write-Host ""
        
        # Step 5: Next steps
        Write-Host "======================================" -ForegroundColor Cyan
        Write-Host "📝 NEXT STEPS:" -ForegroundColor Cyan
        Write-Host "======================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "1️⃣  Remove colons from SHA256 hash above" -ForegroundColor White
        Write-Host "    Example: 14:6D:E9... → 146de9..." -ForegroundColor Gray
        Write-Host ""
        Write-Host "2️⃣  Upload APK to public hosting:" -ForegroundColor White
        Write-Host "    • GitHub Releases (easiest)" -ForegroundColor Gray
        Write-Host "    • Google Drive (quick test)" -ForegroundColor Gray
        Write-Host "    • Firebase Hosting (production)" -ForegroundColor Gray
        Write-Host ""
        Write-Host "3️⃣  Update backend .env file:" -ForegroundColor White
        Write-Host "    APP_DOWNLOAD_URL=https://your-url/app-release.apk" -ForegroundColor Gray
        Write-Host "    APP_SIGNATURE_CHECKSUM=<hash without colons>" -ForegroundColor Gray
        Write-Host ""
        Write-Host"4️⃣  Generate new QR code from backend" -ForegroundColor White
        Write-Host ""
        Write-Host "5️⃣  Factory reset device and scan QR" -ForegroundColor White
        Write-Host ""
        Write-Host "======================================" -ForegroundColor Cyan
        Write-Host "See BUILD_RELEASE_GUIDE.md for details" -ForegroundColor Cyan
        Write-Host "======================================" -ForegroundColor Cyan
        
    } else {
        Write-Host "❌ APK file not found at expected location" -ForegroundColor Red
    }
} else {
    Write-Host ""
    Write-Host "❌ BUILD FAILED" -ForegroundColor Red
    Write-Host "Check the error messages above" -ForegroundColor Yellow
}
