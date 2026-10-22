# Copies your own font to Mii_OS on the TV box. Mii_OS then uses it for all its text,
# the HOME Menu included.
#
# Put one font file in the "my-font" folder next to this script: a .ttf or .otf file.
# It goes to your box only; nothing is uploaded anywhere. Run with -reset (or reset-font.bat)
# to go back to the box's own font.

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$pack = Join-Path $here "my-font"
$reset = $args -contains "-reset"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $onPath) { throw "adb not found. Install Android Studio's SDK Platform-Tools, then run this again." }
    $adb = $onPath.Source
}

if (-not (Test-Path $pack) -and -not $reset) {
    New-Item -ItemType Directory -Path $pack | Out-Null
    Write-Host "Made the folder $pack. Put a .ttf or .otf font in it, then run this again." -ForegroundColor Yellow
    Start-Process $pack
    exit 0
}

$font = $null
if (-not $reset) {
    $font = Get-ChildItem $pack -File | Where-Object { $_.Extension -match '^\.(ttf|otf)$' } | Select-Object -First 1
    if (-not $font) { throw "No .ttf or .otf font in $pack." }
}

# The box's IP can be given as an argument (push-font.ps1 192.168.x.x); otherwise it asks.
$ip = $args | Where-Object { $_ -notlike "-*" } | Select-Object -First 1
if (-not $ip) { $ip = Read-Host "Your TV box's IP address (Settings > Network on the box)" }
& $adb connect "${ip}:5555" | Out-Host

# Android hides font files in shared storage from apps, so the font goes straight into Mii_OS's
# own folder through its pack provider, which only adb may use.
$uri = "content://com.miios.launcher.packs/font"
foreach ($ext in "ttf", "otf") {
    & $adb -s "${ip}:5555" shell content delete --uri "$uri/font.$ext" 2>$null | Out-Null
}
if ($reset) {
    Write-Host "Removed your font. Mii_OS uses the box's own font again." -ForegroundColor Green
    exit 0
}
$ext = $font.Extension.ToLower().TrimStart(".")
# cmd's < keeps the file's bytes exactly as they are
cmd /c "`"$adb`" -s ${ip}:5555 shell content write --uri $uri/font.$ext < `"$($font.FullName)`""
if ($LASTEXITCODE -ne 0) { throw "Couldn't copy the font. Is Mii_OS 0.4.0 or newer installed on the box?" }
Write-Host "Done. Go back to Mii_OS on the TV and it uses your font." -ForegroundColor Green
