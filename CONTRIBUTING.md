# Contributing

Thanks for helping. Issues and pull requests are welcome. Questions and ideas go in
[Discussions](https://github.com/AbhishekKaisar/glassos-tv/discussions).

## Report a bug or a device result

- **Bugs**: use the bug report form. Say which device and system version (Fire TV: Settings > My Fire TV > About;
  Android TV and Google TV: Settings > Device Preferences > About, or Settings > System > About), what you pressed
  and what you saw. A screenshot helps: `adb exec-out screencap -p > screen.png`. A log helps more:
  `adb logcat -d | grep -i glassos`.
- **Device results**: GlassOS is tested on few devices. If you try it on yours, use the device report form, even
  when everything works. Say the model, the Fire OS or Android version and whether the Home button opens GlassOS.
- Issues: https://github.com/AbhishekKaisar/glassos-tv/issues. Look for `good first issue` and `help wanted` if
  you want something to pick up.

## Build

Requirements: JDK 17 and the Android SDK (platform 34, build tools 34.0.0). No keys are needed.

```sh
./gradlew assembleDebug lint   # app/build/outputs/apk/debug/app-debug.apk
```

Without a TMDB key the Top Shelf shows the built-in scenes. To see real trending titles, get a free v3 API key at
[themoviedb.org](https://www.themoviedb.org/settings/api) and put it in `local.properties` (which is not
committed):

```properties
tmdb.apiKey=your_v3_api_key
```

or set the `TMDB_API_KEY` environment variable. A key can also be typed in GlassOS under Settings > TMDB API key.

## Test on an emulator

1. In Android Studio's Device Manager, create a virtual device from the **Television** category (for example
   Television (1080p)) with an Android TV system image, API 28 or newer.
2. Start it, then install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. Start GlassOS from the launcher, or `adb shell am start -n io.github.abhishekkaisar.glassos/.MainActivity`.
4. Drive it with the arrow keys, Enter (OK), Escape (Back) and the Home button. Use `adb shell input keyevent
   KEYCODE_MENU` for the Control Center.

The emulator is Android TV, so Fire TV paths (`Platform.isFireTv`) are not exercised there. If you change code
that both platforms share, say in the pull request what you could and could not test.

## Pull requests

1. Fork the repository and branch from `main`.
2. Keep a change to one thing. Add screenshots for any UI change.
3. Open a pull request against `main` and fill in the template.
4. `main` is protected: changes arrive through pull requests, and the CI build must pass.

## What CI checks

The **Build** workflow runs on every pull request and every push to `main`. It builds the debug APK with
`./gradlew assembleDebug lint` using no TMDB key and no signing key, and uploads the APK as an artifact so you can
install it from the run page. It does not run on a device.

## Code style

Match the surrounding code. Kotlin with plain Android Views, no AndroidX, minSdk 22 (Fire OS 5 and 6 must keep
working). Check both platforms: `Platform.isFireTv` separates Fire TV behaviour from Android TV. Keep drawing
cheap: TV GPUs are slow, so blur once and cache, and check `adb shell dumpsys gfxinfo` before and after a visual
change.

## Never commit secrets

The TMDB key goes in `local.properties` or the `TMDB_API_KEY` environment variable. Signing keys stay outside the
repository. Do not put keystores, passwords or your home network's IP addresses in a commit, an issue or a pull
request.

## Release signing (maintainer)

Release builds read their signing config from `~/.glass-release/keystore.properties` (or the file named by the
`GLASS_KEYSTORE_PROPERTIES` environment variable), outside the repository:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `assembleRelease` builds an unsigned APK. Contributors do not need this.

With each release, update the APK name and its direct link in the README's Install section: releases are
pre-releases, so GitHub's `releases/latest` link does not find them.

## Licence

GlassOS is licensed under the GNU GPL v3. By contributing you agree that your work is licensed under the same terms.
