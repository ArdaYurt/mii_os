# Mi Box speed-up

Google TV runs a lot of apps in the background that a Mii_OS box never uses. This script turns them
off and applies a few speed settings, so the box has more free memory and feels snappier. It needs no
root, and it doesn't delete anything: every app is only switched off, and one command switches all of
it back on.

Apple TV, YouTube, Plex, Prime Video, Netflix, the Play Store, Chromecast, the voice remote, 4K, HDR,
Dolby Vision and the DRM that streaming apps need are all left alone.

## What it does

**Option 1, install or update Mii_OS.** Downloads the latest Mii_OS, installs it on the box, pre-compiles
it so it opens and scrolls faster, and opens it on the TV. If you put a `mii-os.apk` in this folder it
uses that file instead.

**Option 2, safe cleanup.** Turns off apps nobody uses on a TV box: Send feedback, TalkBack, Play
Games, printing, Device Health Services, YouTube TV and the old recommendation
rows. The full list is in [`packages-safe.txt`](packages-safe.txt). It also freezes apps waiting in the
background so they stop using the processor, clears app caches, and pre-compiles Mii_OS if it's
installed.

Android's animation speed stays at normal. Many guides set it to half speed, but on this box that
skips frames and looks choppy, so the script puts it back to normal if an earlier version changed it.

**Option 3, full speed-up.** Everything in option 2, plus the stock Google TV home screen and the apps
that feed it (Google TV films and shows, YouTube Music, and the setup wizard, which can
take the home screen back after a restart). These use the most memory, and afterwards Mii_OS is the
box's only home screen. See [`packages-full.txt`](packages-full.txt). Only choose this once you're
happy with Mii_OS as your home. This is the step that makes the biggest difference: options 1 and 2
alone leave the stock Google TV home running.

The screensavers are always left on (and switched back on if an earlier version turned them off).
Without a screensaver, Android puts the box to sleep at the moment the screensaver would start, so
it looks as if the box switched itself off.

**Option 4, undo** turns everything back on and resets the settings. `mii-restore.bat` does the same.

## How to run it

You need your Windows laptop with `adb` (platform-tools), on the same Wi-Fi as the box.

1. On the box: *Settings › System › About*, press *Android TV OS build* seven times. Then open
   *Settings › System › Developer options* and turn on **USB debugging**.
2. On the laptop, download this repository (*Code › Download ZIP* on GitHub) and unzip it. Copy the
   `tools\speedup` folder into your `platform-tools` folder, or anywhere you like. The script also finds
   adb in Android Studio's SDK folder and in `C:\platform-tools`.
3. Double-click **`mii-speedup.bat`**. Type the box's IP address when it asks (on the box:
   *Settings › Network & Internet ›* your Wi-Fi). The first time, the TV asks *Allow USB debugging?*:
   tick *Always allow* and press OK.
4. Check the name it shows is your box and press **Y**. If it shows the emulator instead, press **N**
   and it asks for the IP. Then choose an option.
5. Press **Y** to restart the box. Give it a few minutes after the restart before judging speed:
   Android does background work right after it starts.

Each line says **OK** when it worked, **-** when that app isn't on your box (fine), or **?** when the
box refused (also fine; nothing changed).

## If something's wrong

Double-click **`mii-restore.bat`** and press **Y**. Every app comes back on and the box is the same as
before. If the Mi Box ever won't show a home screen after option 2, the restore script still works
because it talks to the box over Wi-Fi.

## Extra, if you never use the mic button

Google Assistant also runs in the background. If you never use voice search, you can turn it off too
(it stops the mic button working):

```
adb shell pm disable-user --user 0 com.google.android.katniss
```

Turn it back on with `adb shell pm enable com.google.android.katniss`.
