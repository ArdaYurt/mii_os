# Copies your own channel logos to Mii_OS on the TV box.
#
# Put images in the "my-logos" folder next to this script:
#   - 16:9, ideally 3840x2160 (1920x1080 is also sharp; the box draws its screen at 1080p)
#   - PNG, WebP or JPG
#   - named after the channel: apple-tv, youtube, plex, prime-video, stremio, screen-share, tv,
#     disney-plus, mubi, hbo-max, spotify, kick ... or any app's name in lowercase with dashes
#     (for example "Pluto TV" -> pluto-tv.png), or its package name.
# Your images replace Mii_OS's own art for those channels. They go to your box only.
# Run with -reset (or reset-logos.bat) to remove them all.

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$pack = Join-Path $here "my-logos"
# A shared folder: some boxes refuse adb writes into Mii_OS's own Android/data folder.
$target = "/sdcard/Mii_OS/logos/"
$oldTarget = "/sdcard/Android/data/com.miios.launcher/files/logos/"
$reset = $args -contains "-reset"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $onPath) { throw "adb not found. Install Android Studio's SDK Platform-Tools, then run this again." }
    $adb = $onPath.Source
}

if (-not (Test-Path $pack) -and -not $reset) {
    New-Item -ItemType Directory -Path $pack | Out-Null
    Write-Host "Made the folder $pack. Put your logo images in it, then run this again." -ForegroundColor Yellow
    Start-Process $pack
    exit 0
}

$files = @()
if (-not $reset) {
    $files = Get-ChildItem $pack -File | Where-Object { $_.Extension -match '^\.(png|webp|jpg|jpeg)$' }
    if (-not $files) { throw "No PNG, WebP or JPG images in $pack." }
}

# The box's IP can be given as an argument (push-logos.ps1 192.168.x.x); otherwise it asks.
$ip = $args | Where-Object { $_ -notlike "-*" } | Select-Object -First 1
if (-not $ip) { $ip = Read-Host "Your TV box's IP address (Settings > Network on the box)" }
& $adb connect "${ip}:5555" | Out-Host
& $adb -s "${ip}:5555" shell mkdir -p $target | Out-Null
# Lets Mii_OS read the shared folder (asked once; harmless to repeat).
& $adb -s "${ip}:5555" shell pm grant com.miios.launcher android.permission.READ_EXTERNAL_STORAGE | Out-Null
if ($reset) {
    & $adb -s "${ip}:5555" shell "rm -f ${target}* ${oldTarget}*" 2>$null | Out-Null
    Write-Host "Removed your logos. Mii_OS shows its own art again." -ForegroundColor Green
    exit 0
}
foreach ($f in $files) {
    $base = $f.BaseName.ToLower()
    & $adb -s "${ip}:5555" shell "rm -f ${target}${base}.*" | Out-Null
    & $adb -s "${ip}:5555" push $f.FullName "${target}${base}$($f.Extension.ToLower())" | Out-Host
}
Write-Host "Done. Go back to Mii_OS on the TV and your logos appear." -ForegroundColor Green
