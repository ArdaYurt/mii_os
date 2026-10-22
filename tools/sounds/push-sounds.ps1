# Copies your own sound pack to Mii_OS on the TV box.
#
# Put audio files in the "my-sounds" folder next to this script, named after the moment they play:
#   move     moving between channels and buttons
#   select   opening a channel, a button or Settings
#   back     closing something
#   launch   pressing Start
#   page     turning the page of channels
#   home     the HOME Menu opening
#   notice   a message, like "update available"
#   music    the home-screen music (it loops)
# Any of .mp3 .ogg .wav .m4a .flac works (for example: move.wav, launch.mp3). Leave a sound out to
# keep Mii_OS's own. The files go to your box only; nothing is uploaded anywhere.

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$pack = Join-Path $here "my-sounds"
$target = "/sdcard/Android/data/com.miios.launcher/files/sounds/"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $onPath) { throw "adb not found. Install Android Studio's SDK Platform-Tools, then run this again." }
    $adb = $onPath.Source
}

if (-not (Test-Path $pack) -and -not ($args -contains "-reset")) {
    New-Item -ItemType Directory -Path $pack | Out-Null
    Write-Host "Made the folder $pack. Put your sound files in it (see the names at the top of this script), then run this again." -ForegroundColor Yellow
    Start-Process $pack
    exit 0
}

$reset = $args -contains "-reset"
$names = "move", "select", "back", "launch", "page", "home", "notice", "music"
$files = Get-ChildItem $pack -File | Where-Object { $names -contains $_.BaseName.ToLower() -and $_.Extension -match '^\.(mp3|ogg|wav|m4a|flac)$' }
if (-not $files -and -not $reset) { throw "No sound files with the right names in $pack." }

$ip = Read-Host "Your TV box's IP address (Settings > Network on the box)"
if ($args -contains "-reset") {
    & $adb connect "${ip}:5555" | Out-Host
    & $adb -s "${ip}:5555" shell "rm -f ${target}*" | Out-Null
    Write-Host "Removed your sound pack. Mii_OS plays its own sounds again." -ForegroundColor Green
    exit 0
}
& $adb connect "${ip}:5555" | Out-Host
& $adb -s "${ip}:5555" shell mkdir -p $target | Out-Null
# Remove old versions with other extensions, so the new file is the one Mii_OS picks.
foreach ($f in $files) {
    $base = $f.BaseName.ToLower()
    & $adb -s "${ip}:5555" shell "rm -f ${target}${base}.*" | Out-Null
    & $adb -s "${ip}:5555" push $f.FullName "${target}${base}$($f.Extension.ToLower())" | Out-Host
}
Write-Host "Done. Go back to Mii_OS on the TV and the new sounds play from now on." -ForegroundColor Green
