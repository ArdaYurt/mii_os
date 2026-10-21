# Mii_OS

A modern Wii-style home screen for Android TV, with Apple TV-style focus motion and Liquid Glass panels.
Built for the Xiaomi Mi Box S (2nd gen, Google TV).

Mii_OS is a launcher app, not a replacement operating system. It changes the home screen only, so
Apple TV, Plex, YouTube, Netflix and every other app keep their 4K, HDR and Dolby Vision playback
exactly as before. Uninstalling it puts the box back the way it was.

## Try it on your laptop first

You can check the design on a Windows, Mac or Linux laptop with Android Studio's free TV emulator,
then install the same file on the Mi Box.

1. Install [Android Studio](https://developer.android.com/studio) and open it.
2. On the welcome screen choose *More Actions › Virtual Device Manager*, then the **+** button.
3. Pick the **TV** category and the **Television (1080p)** device. (**Television (4K)** also works
   but runs slowly on most laptops; the Mi Box draws its home screen at 1080p anyway.)
4. For the system image choose a **Google TV** image with API level **31 or higher** and let it
   download. Finish, then press ▶ to start the emulator.
5. Download [`mii-os.apk`](https://github.com/ArdaYurt/mii_os/releases/latest/download/mii-os.apk)
   on the laptop and drag the file onto the emulator window. It installs in a few seconds.
6. Open Mii_OS from the emulator's apps row. Use the keyboard as the remote: arrow keys to move,
   **Enter** for OK, **Esc** for Back.

The emulator shows the layout, colours and motion, but it runs on your laptop's hardware, so judge
speed on the Mi Box itself. Your own apps (Plex, Apple TV and so on) won't be in the emulator unless
you install them there, so most channels will show as empty slots.

## Install it on your Mi Box

You only need the TV remote. It takes about five minutes the first time.

1. **Allow app installs.** On the box open *Settings › System › About*, scroll to
   *Android TV OS build* and press it seven times until it says you're a developer.
2. **Get the Downloader app.** Open the Play Store on the box, search for **Downloader** (by AFTVnews)
   and install it. Then go to *Settings › Apps › Security & restrictions › Unknown sources* and turn
   **Downloader** on.
3. **Download Mii_OS.** Open Downloader and type this address:

   `https://github.com/ArdaYurt/mii_os/releases/latest/download/mii-os.apk`

   Press *Go*, then *Install* when the download finishes. (This link only works while the repository
   is public. If it's private, download `mii-os.apk` from the Releases page on your phone and send it
   to the TV with the *Send Files to TV* app instead.)
4. **Make it your home screen.** The speed-up script on your laptop does this (its "make Mii_OS the
   home screen" option), so the Home button on your remote opens Mii_OS.

### Updates

Mii_OS updates itself. When a new version is out, a pop-up asks **Update** or **Later**. Press OK on
**Update** and confirm in Android's installer. The first time, Android asks you to allow Mii_OS to
install apps: turn it on and press Back.

The in-app updater needs the builds to be signed with one permanent key. Set that up once by running
[`tools/signing/make-key.bat`](tools/signing/make-key.bat) on your Windows laptop (see *Building*), then
uninstall Mii_OS and install it one last time with step 3.

### Favourite channels

Apple TV, YouTube, Plex, Prime Video, Stremio and Screen Share (AirScreen) always have the first channels,
with Mii_OS's own art. If one isn't installed yet its channel is greyed out with an **Install** tag;
open it and press **Install** to go to its Play Store page.

### iPhone and iPad screen sharing

The box can't receive AirPlay on its own. Install **AirScreen** from the Screen Share channel. Open it,
then on your iPhone or iPad use *Screen Mirroring* in Control Center and pick the TV.

### TV channel (live IPTV)

The **TV** channel plays live TV inside Mii_OS from your own IPTV playlist. Mii_OS doesn't come with
any channels: you add the M3U playlist address your IPTV provider gives you (with Xtream-style logins
it looks like `http://host:port/get.php?username=…&password=…&type=m3u_plus&output=ts`).

1. Open the **TV** channel and press **Start**. The first time, the setup screen opens.
2. Easiest: on your phone, open the address shown at the bottom of the screen (for example
   `http://192.168.1.20:8080`), paste the playlist address and press **Send to TV**. Your phone
   must be on the same Wi-Fi. Or press OK on **Playlist address** and type it with the remote.
3. If your provider gives a separate guide (XMLTV/EPG) address, add it the same way. Many playlists
   include one already.
4. Choose **Watch TV**. Mii_OS remembers the playlist and opens on your last channel next time.

| Remote, while watching | What it does |
|---|---|
| ↑ / ↓ (or Channel +/−) | Next / previous channel |
| OK or ← | Channel list. ← → change group, OK tunes, the ⚙ row at the top changes the playlist |
| → | Show what's on now and next |
| Number keys | Go to a channel number |
| Back | Hide the info, then back to the home screen |

To change the playlist later, use the ⚙ row at the top of the channel list, or **Menu** on the
TV channel screen in the launcher.

## Using it

| Remote | What it does |
|---|---|
| ← ↑ → ↓ | Move between channels. Going past the right or left edge turns the page. |
| Down from the bottom row | Reach the dock: **Settings** opens the Mii_OS Settings screen, **Share** opens AirScreen for iPhone/iPad screen sharing. |
| OK | The channel grows into its channel screen. **Start** launches the app, **Menu** shows its app info. |
| Back | Close the channel screen. |
| Home | Return to the first page. |

## Design and speed

- **Speed first.** Release builds are minified and ship Compose's startup profiles. Nothing on the
  home screen animates while you're not touching the remote, so the box stays idle and cool.
- **Motion.** Every transition is a spring on GPU-only properties (scale, tilt, slide, fade), defined in
  [`Motion.kt`](app/src/main/java/com/miios/launcher/ui/Motion.kt) so the whole launcher moves with one feel.
- **Wii colours.** White and soft grey with the Wii's sky blue for focus, in
  [`Theme.kt`](app/src/main/java/com/miios/launcher/ui/Theme.kt).
- **Glass.** The dock, round buttons and channel-screen bar blur the scene behind them. The scene is
  static, so each blur is rendered once and reused.
- **Channel art.** For each app Mii_OS uses, in order: an image from
  [`assets/channel_art`](app/src/main/assets/channel_art) named after the app's package, the app's own
  TV banner, or its icon on a matching colour. Art is decoded at the size it's drawn.
- **Favourite channels** and their art are listed in
  [`ChannelStyles.kt`](app/src/main/java/com/miios/launcher/art/ChannelStyles.kt). The art is drawn as
  shapes and text, so it is sharp at any resolution.

Visual reference: [interactive mockup](https://claude.ai/artifact/YPhLo3wLJRHAAZicFzVCeF).

## Building

GitHub Actions builds `mii-os.apk` on every push and publishes it as the `latest` release.
Locally: `./gradlew assembleRelease` with JDK 17 and the Android SDK (API 35).

Release builds are signed with the permanent key from the repository secrets `MII_KEYSTORE_BASE64`
and `MII_KEYSTORE_PASSWORD` (optionally `MII_KEY_ALIAS` and `MII_KEY_PASSWORD`). Run
[`tools/signing/make-key.bat`](tools/signing/make-key.bat) once on Windows to make the key and add the
secrets; the key stays on your computer and in GitHub's secrets, never in this public repository.
Without the secrets each build gets a throwaway key, updates need an uninstall first, and the in-app
updater stays off (CI only publishes `version.json` for permanently signed builds).

If Mii_OS crashes, the next start shows the error, and the full trace is in `files/last_crash_seen.txt`
(`adb shell run-as com.miios.launcher cat files/last_crash_seen.txt`).

### Known limits

- The box draws its interface at 1080p and scales it to 4K, like the stock home screen. Mii_OS's own
  channel art is drawn as shapes, so it stays sharp; other apps' banners are loaded at their highest
  resolution, but some apps only ship small ones.
