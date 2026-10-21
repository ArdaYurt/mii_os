# Mi Box speed-up

Google TV runs a lot of apps in the background that a Mii_OS box never uses. This script turns them
off and applies a few speed settings, so the box has more free memory and feels snappier. It needs no
root, and it doesn't delete anything: every app is only switched off, and one command switches all of
it back on.

Apple TV, YouTube, Plex, Prime Video, Netflix, the Play Store, Chromecast, the voice remote, 4K, HDR,
Dolby Vision and the DRM that streaming apps need are all left alone.

## What it does

**Option 1, safe cleanup** (start here). Turns off apps nobody uses on a TV box: Send feedback,
TalkBack, Play Games, printing, the basic screensaver, Device Health Services, YouTube TV and the old
recommendation rows. The full list is in [`packages-safe.txt`](packages-safe.txt). It also:

- makes Android's own menu and app-switch animations twice as fast (Mii_OS's own animations keep their
  full length, so they still feel smooth),
- freezes apps waiting in the background so they stop using the processor,
- pre-compiles Mii_OS so it opens faster.

**Option 2, full speed-up.** Everything in option 1, plus the stock Google TV home screen and the apps
that feed it (Google TV films and shows, Ambient mode, YouTube Music). These are the biggest memory
users, and afterwards Mii_OS is the box's only home screen. See
[`packages-full.txt`](packages-full.txt). Only choose this once you're happy with Mii_OS as your home.

**Option 3, undo** turns everything back on and resets the settings. `mii-restore.bat` does the same.

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
4. Check the name it shows is your box, press **Y**, then **1** (or **2**).
5. Press **Y** to restart the box. Done.

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
