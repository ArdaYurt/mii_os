# Makes Mii_OS's permanent signing key and stores it in the repository's GitHub secrets.
#
# Android only updates an app with a build signed by the same key. Until this runs, every build
# is signed with a throwaway key, so each update needs an uninstall first and the in-app updater
# stays off. Run it once, on your own computer. The key never goes into the (public) repository.

$ErrorActionPreference = "Stop"
$repo = "ArdaYurt/mii_os"
$dir = Join-Path $HOME "mii-os-signing"
$keystore = Join-Path $dir "mii-os.jks"

function Find-Keytool {
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    )
    if ($env:JAVA_HOME) { $candidates += "$env:JAVA_HOME\bin\keytool.exe" }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw "keytool not found. Install Android Studio (it includes keytool) and run this again."
}

if (Test-Path $keystore) {
    Write-Host "A key already exists at $keystore. Using it." -ForegroundColor Yellow
    $password = Read-Host "Its password"
} else {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $password = [Convert]::ToBase64String($bytes) -replace '[+/=]', 'x'
    $keytool = Find-Keytool
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias mii-os -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass $password -keypass $password -dname "CN=Mii_OS" | Out-Null
    Set-Content -Path (Join-Path $dir "password.txt") -Value $password
    Write-Host "Made a new key in $dir. Keep that folder: losing it means one more uninstall later." -ForegroundColor Green
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($gh) {
    $base64 | gh secret set MII_KEYSTORE_BASE64 --repo $repo
    $password | gh secret set MII_KEYSTORE_PASSWORD --repo $repo
    Write-Host "Done: both secrets are saved on GitHub." -ForegroundColor Green
    exit 0
}

# No GitHub CLI: paste the two secrets on the website, one at a time.
$page = "https://github.com/$repo/settings/secrets/actions/new"
Write-Host ""
Write-Host "Your browser will open GitHub's 'New secret' page. Two secrets to add:" -ForegroundColor Cyan
Set-Clipboard -Value $base64
Start-Process $page
Write-Host "1. Name: MII_KEYSTORE_BASE64   Secret: press Ctrl+V (it's already copied). Then 'Add secret'."
Read-Host "   Press Enter here when that's saved"
Set-Clipboard -Value $password
Start-Process $page
Write-Host "2. Name: MII_KEYSTORE_PASSWORD Secret: press Ctrl+V (copied now). Then 'Add secret'."
Read-Host "   Press Enter here when that's saved"
Set-Clipboard -Value " "
Write-Host "All set. The next build is signed with your permanent key." -ForegroundColor Green
