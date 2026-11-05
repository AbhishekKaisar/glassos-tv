# AGENTS.md

This file is for AI coding agents. Part 1 is for an agent asked to install GlassOS on someone's TV. Part 2 is for an
agent working on the code.

---

# Part 1: Installing GlassOS on someone's TV

GlassOS is a free home screen for Android TV, Google TV and Amazon Fire TV (https://github.com/AbhishekKaisar/glassos-tv).
The person asking you may not be technical. Do the work for them, and talk in plain words.

## Safety rules (read first)

1. Before each step, say in one or two plain sentences what you are about to do and why. Then do it.
2. Install only two things: `adb` (from an official source, step 3) and the verified GlassOS APK (step 5). Nothing else.
3. Never disable, uninstall or change the TV's own apps. Never reboot the TV, factory reset it or change system
   settings beyond the grants listed in step 6, unless the person explicitly asks.
4. Never send the person's data anywhere. Do not upload their IP address, logs or screenshots.
5. Download the APK only from the official release page (step 5). Never from mirrors, forums or links sent by others.
6. If a step fails, stop and explain what happened in plain words. Do not try risky workarounds.
7. The "turn off the stock launcher" option (end of step 6) only on the person's explicit request, after the warning
   and with the undo command shown.
8. If you cannot run commands on the person's computer, give them the exact commands one at a time instead.

## Step 1: Ask two simple questions

1. "Which kind of TV is it: Fire TV (Amazon), Android TV, or Google TV?" If unsure: a Fire TV Stick or Fire TV
   Edition TV is Fire TV; Chromecast with Google TV and Google TV Streamer are Google TV; Sony, Philips, TCL, Hisense
   and Xiaomi boxes or TVs are usually Android TV or Google TV (the home screen says which).
2. "What is the TV's IP address, and is your computer on the same Wi-Fi or network as the TV?" Where to find it:
   - Fire TV: Settings > My Fire TV > About > Network.
   - Android TV and Google TV: Settings > Network & Internet > the connected network (the IP address is shown there,
     or under Advanced / Network status). On some builds: Settings > Device Preferences > About > Status.

Ask nothing else unless a step needs it. Use `<ip>` below for the address.

## Step 2: Turn on debugging on the TV (the person does this with the remote)

Give the person the path for their platform and wait until they say it is done.

- **Fire TV**
  1. Settings > My Fire TV > Developer options. If there is no Developer options entry: Settings > My Fire TV >
     About, then press OK on the device name 7 times.
  2. Turn on **ADB debugging**. Turn on **Apps from Unknown Sources** if it is there.
- **Android TV and Google TV**
  1. Settings > Device Preferences (on Google TV: Settings > System) > About, then press OK on **Build** 7 times.
  2. Back, then Developer options. Turn on **USB debugging**, and **Network debugging** if the TV has it.
  3. Android 11 and later with only **Wireless debugging**: open Wireless debugging, choose **Pair device with
     pairing code**. The TV shows a six-digit code and an address like `192.0.2.10:37099` (the pairing port). Ask
     the person to read both to you. Then run `adb pair <ip>:<pairing-port>` and enter the code. After pairing, the
     Wireless debugging screen shows a different address and port for connecting; use that in step 4 instead of
     `:5555`.

## Step 3: Install adb from an official source

Check first: `adb version`. If it works, skip this step.

- **macOS**: `brew install --cask android-platform-tools` (needs Homebrew; if missing, use the zip below).
- **Windows** (PowerShell): `winget install Google.PlatformTools`, then open a new terminal.
- **Linux**: `sudo apt install adb` (Debian, Ubuntu), `sudo dnf install android-tools` (Fedora) or
  `sudo pacman -S android-tools` (Arch). Or the zip from Google: https://developer.android.com/tools/releases/platform-tools
  (unzip it and run `./platform-tools/adb`).

The sudo password is the person's; let them type it. Do not install anything else.

## Step 4: Connect

```sh
adb kill-server
adb connect <ip>:5555
adb devices
```

Tell the person: "Look at the TV. It asks 'Allow USB debugging?'. Tick **Always allow from this computer** and choose
**Allow**." Then run `adb devices` again. The TV must show as `device`.

- `unauthorized`: the prompt was not accepted. Run `adb kill-server`, `adb connect <ip>:5555` again and ask the person
  to look at the TV. If no prompt appears, ask them to turn ADB debugging off and on in Developer options (this is
  allowed).
- `offline`: `adb disconnect <ip>:5555`, `adb kill-server`, `adb connect <ip>:5555`. If still offline, ask the person
  to unplug the TV or stick for ten seconds and plug it in again (they do this, you do not reboot it).
- `No route to host`, `Connection refused` or a timeout: check the computer and TV are on the same Wi-Fi (not a
  guest network), the IP is right (it can change), and debugging is on. On Android 11 and later with Wireless
  debugging, the port is not 5555; use the address from the Wireless debugging screen.
- More than one device listed: use `adb -s <ip>:5555 ...` for every command below.

## Step 5: Download and verify the APK

Use the official release page only: https://github.com/AbhishekKaisar/glassos-tv/releases. GitHub's "latest" shortcut
ignores pre-releases, and GlassOS is currently in beta, so take the newest release from the list.

```sh
mkdir glassos-install && cd glassos-install
# With the GitHub CLI:
gh release download --repo AbhishekKaisar/glassos-tv --pattern 'GlassOS-*.apk' "$(gh release list --repo AbhishekKaisar/glassos-tv --limit 1 --json tagName --jq '.[0].tagName')"
# Without it (macOS, Linux, Windows 10+ have curl):
curl -s "https://api.github.com/repos/AbhishekKaisar/glassos-tv/releases?per_page=1" | grep browser_download_url
curl -L -o GlassOS.apk "<the browser_download_url printed above>"
```

The URL must start with `https://github.com/AbhishekKaisar/glassos-tv/releases/download/`.

Then check who signed it. The official release is signed with this certificate:

```
SHA-256: AA:68:97:AB:6E:00:8C:EB:7D:6F:30:F7:18:F8:5F:84:77:E7:37:04:88:16:F9:0C:5D:48:39:08:70:48:AC:E7
Owner:   CN=GlassOS, O=AbhishekKaisar
```

Either of these prints it (`keytool` comes with any JDK, `apksigner` with the Android SDK build tools):

```sh
keytool -printcert -jarfile GlassOS.apk
apksigner verify --print-certs GlassOS.apk
```

Compare the SHA-256 digest. **If it does not match exactly, stop.** Tell the person the file is not the official
GlassOS, delete it and do not install it. If neither tool is installed, say so and ask the person whether to go on
without the check; do not install a JDK without their agreement. (The release notes also give the APK's own SHA-256;
`shasum -a 256 GlassOS.apk` on macOS and Linux, `Get-FileHash GlassOS.apk` on Windows.)

## Step 6: Install, grant, set as home

```sh
adb install -r GlassOS.apk
```

A failure `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means a copy signed with a different key is on the TV; stop and tell
the person (do not uninstall it unless they agree).

Grants, for both platforms (they let GlassOS cover the stock home, show Now Playing, sleep the TV and read the
profile photo; everything works without them, with less):

```sh
P=io.github.abhishekkaisar.glassos
adb shell appops set $P GET_USAGE_STATS allow
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell dpm set-active-admin $P/.AdminReceiver
adb shell pm grant $P android.permission.READ_EXTERNAL_STORAGE
```

Notification access (Now Playing, Do Not Disturb). First find the version: `adb shell getprop ro.build.version.sdk`.

- API 26 or higher (Android 8 and later, Fire OS 7 and later):
  `adb shell cmd notification allow_listener $P/$P.MediaListener`
- Lower (Fire OS 5 and 6). Look first: `adb shell settings get secure enabled_notification_listeners`. If it prints
  `null` or nothing: `adb shell settings put secure enabled_notification_listeners $P/$P.MediaListener`. If it prints
  something, keep it and append after a colon: `...listeners "<existing>:$P/$P.MediaListener"`.

If a command prints an error, note it and carry on; GlassOS still runs. On a TV with no device admin at all (the
Nvidia Shield), `dpm set-active-admin` prints Success but changes nothing; GlassOS then does not offer Sleep.

Platform-specific:

- **Fire TV**: Fire OS does not let apps replace its home. Nothing more to set. GlassOS notices the Fire TV home and
  covers it, which takes about a second after the Home button.
- **Android TV and Google TV**: make GlassOS the default home app:
  `adb shell cmd package set-home-activity $P/.MainActivity`.
  Many builds keep their own home anyway (the stock launcher outranks other apps). GlassOS detects that and covers the
  stock home instead. That is normal, not a failure. Do **not** disable the stock launcher unless the person asks.

Advanced, only if the person explicitly asks for Home to open GlassOS directly on a TV that keeps its stock home.
Warn them first: "This turns off the TV's own launcher. If you uninstall GlassOS without turning it back on, the TV
has no home screen." Then:

```sh
# Google TV: com.google.android.apps.tv.launcherx   Android TV: com.google.android.tvlauncher
adb shell pm disable-user --user 0 com.google.android.tvlauncher
# undo
adb shell pm enable com.google.android.tvlauncher
```

The Nvidia Shield turns its stock launcher back on at every restart; tell the person, so they know Home goes back
to the stock home after a restart unless they run the command again.

Launch it:

```sh
adb shell am start -n io.github.abhishekkaisar.glassos/.MainActivity
```

GlassOS's own first-run setup then starts on the TV (name, favourite apps, permissions). Tell the person to go through
it with the remote.

## Step 7: Check and report

```sh
adb shell dumpsys window | grep mCurrentFocus
```

(On Windows PowerShell use `adb shell dumpsys window | Select-String mCurrentFocus`.) The output should name
`io.github.abhishekkaisar.glassos`. Then tell the person, in plain words, what happened: that it is installed and
which signature check passed; which grants worked and which printed an error; whether Home opens GlassOS directly or
GlassOS covers the stock home with a short delay; and that setup is on the TV now. If Home does not return to
GlassOS, point them to Settings > Permissions in GlassOS. Do not claim more than you saw.

Optionally mention they can disconnect with `adb disconnect <ip>:5555` and turn debugging off on the TV; GlassOS
keeps working.

## Step 8: Undo and uninstall

First, if the stock launcher was turned off earlier, turn it back on:
`adb shell pm enable com.google.android.tvlauncher` (or `com.google.android.apps.tv.launcherx` on Google TV).

- **Keep GlassOS but use the stock home**: Android TV and Google TV: Settings > Apps > Default apps > Home app (or
  Settings > Apps > Special app access > Default home app) and choose the stock home. On any platform, in GlassOS:
  Settings > Replace Fire TV home (or Android TV home) > Off.
- **Remove GlassOS**: in GlassOS, Settings > **Prepare to uninstall** (the person does this with the remote). Or by
  adb:
  ```sh
  P=io.github.abhishekkaisar.glassos
  adb shell dpm remove-active-admin $P/.AdminReceiver
  adb uninstall $P
  ```
  Android then goes back to the stock home on its own. Android blocks uninstalling an active device admin, which is
  why the first command comes first.

---

# Part 2: Working on the code

## Build

JDK 17 and the Android SDK (platform 34, build tools 34.0.0). No keys needed.

```sh
./gradlew assembleDebug lint     # app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/build.yml`) runs the same command on every pull request and must pass. `main` is protected:
open a pull request.

## Layout

All code is in `app/src/main/java/io/github/abhishekkaisar/glassos/`. Kotlin, plain Android Views, no AndroidX,
minSdk 22 (Fire OS 5 and 6 must keep working).

- `Platform.kt`: Fire TV versus Android TV (`isFireTv`), stock homes, `isDefaultHome`, Settings intents. Platform
  differences belong here or behind `Platform.isFireTv`, not scattered.
- `MainActivity.kt`, `HomeScreen.kt`, `HeroView.kt`, `HeroRepo.kt`, `Tmdb.kt`: home, Top Shelf and its data.
- `ControlCenter.kt`, `SysControl.kt`: Control Center and the system actions behind it.
- `Setup.kt`, `Grants.kt`, `AdbClient.kt`, `RootShell.kt`: first-run setup and the grants, over the TV's own ADB
  or with root on a rooted TV.
- `HomeWatcher.kt`, `HomeOverlay.kt`, `BootReceiver.kt`: covering the stock home and starting at boot.

## Test

Create an Android TV emulator (Device Manager > Television, API 28 or newer), `adb install -r` the debug APK and drive
it with the arrow keys, Enter, Escape and Home. The emulator does not exercise Fire TV paths; say in the pull request
what you could not test. See CONTRIBUTING.md.

## Rules

- No secrets in commits: no TMDB key, keystore, passwords or private IP addresses. `local.properties` and
  `~/.glass-release` stay untracked.
- Match the surrounding code style. Keep drawing cheap (TV GPUs are slow: blur once and cache).
- Keep the look identical unless the change is about design. Add before and after screenshots for any UI change.
- Do not add a dependency without a strong reason.
