# Copies your own pointer (the hand) to Mii_OS on the TV box.
#
# Put one image named hand.png in the "my-cursor" folder next to this script:
#   - PNG with a transparent background, about 256 pixels wide (any size works)
#   - the hand pointing up, its fingertip touching the top edge a little left of centre
# It goes to your box only. Run with -reset (or reset-cursor.bat) to get Mii_OS's own hand back.

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$pack = Join-Path $here "my-cursor"
$target = "/sdcard/Mii_OS/cursor/"
$reset = $args -contains "-reset"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $onPath) { throw "adb not found. Install Android Studio's SDK Platform-Tools, then run this again." }
    $adb = $onPath.Source
}

if (-not (Test-Path $pack) -and -not $reset) {
    New-Item -ItemType Directory -Path $pack | Out-Null
    Write-Host "Made the folder $pack. Put your hand.png in it, then run this again." -ForegroundColor Yellow
    Start-Process $pack
    exit 0
}
$file = $null
if (-not $reset) {
    $file = Get-ChildItem $pack -File | Where-Object { $_.Name -match '^hand\.(png|webp)$' } | Select-Object -First 1
    if (-not $file) { throw "No hand.png in $pack." }
}

# The box's IP can be given as an argument (push-cursor.ps1 192.168.x.x); otherwise it asks.
$ip = $args | Where-Object { $_ -notlike "-*" } | Select-Object -First 1
if (-not $ip) { $ip = Read-Host "Your TV box's IP address (Settings > Network on the box)" }
& $adb connect "${ip}:5555" | Out-Host
& $adb -s "${ip}:5555" shell mkdir -p $target | Out-Null
& $adb -s "${ip}:5555" shell pm grant com.miios.launcher android.permission.READ_EXTERNAL_STORAGE | Out-Null
& $adb -s "${ip}:5555" shell "rm -f ${target}hand.*" | Out-Null
if ($reset) {
    Write-Host "Removed your pointer. Mii_OS shows its own hand again." -ForegroundColor Green
    exit 0
}
& $adb -s "${ip}:5555" push $file.FullName "${target}$($file.Name.ToLower())" | Out-Host
Write-Host "Done. Go back to Mii_OS on the TV and your pointer appears." -ForegroundColor Green
