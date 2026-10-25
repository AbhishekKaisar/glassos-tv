# Changelog

## Unreleased

- Fire TV: after Home, the focus sound and a tile's focus animation no longer repeat on their own every few seconds,
  and the first top row app no longer stays enlarged for a moment. Package broadcasts that change nothing shown now
  refresh the tiles in place instead of rebuilding them, and only a key press plays the focus sound (#16).

## 1.0.2-beta (2026-10-23)

- Hold OK on any app to move it, like Apple TV: the tile lifts and wiggles, the arrows move it across the top row
  and the apps below, OK drops it and Back puts everything back. A full top row passes its last app down to the
  front of the drawer. Menu while moving adds the app to or removes it from the top row, or hides it.
- Every app can now be re-ordered, not only the top row. The order is kept; new apps go at the end and uninstalled
  apps drop out. Until you move an app the drawer stays alphabetical.
- Hidden apps: hide an app from its menu, or choose apps in Settings > Hidden apps, which also shows them again.
  Hidden apps stay out of the drawer, the top row and the top row picker, and still open from the Top Shelf.
- A hold of OK on an app no longer adds it to or removes it from the top row; that is in the move menu now.
- Edit top row: the other tiles keep jiggling after an app is moved.
- One-tap permission setup: when the TV's ADB accepts the connection and then closes it (Fire OS since 7.6.6.9 and
  8.1.0.3, and late Fire OS 6, block apps from using the TV's own ADB), setup now says so and points to the manual
  grants from a computer. Other ADB errors show the exception and its message.

## 1.0.1-beta (2026-10-22)

GlassOS now targets Android TV and Google TV as well as Fire TV. The repository moved to
https://github.com/AbhishekKaisar/glassos-tv (the old URL redirects).

- Detects the platform (Fire TV, or Android TV / Google TV) and keeps every Fire TV behaviour as it was.
- Android TV: a new setup step, "Make GlassOS your home screen", that tries the system's default home prompt, the
  Default home app page, then `cmd package set-home-activity` over the TV's own ADB. Also in Settings > Home screen.
- When GlassOS is the default home app, the home watcher does not run at all; Home and boot open GlassOS directly.
- Builds that keep their own home (the stock launcher outranks the chosen default) fall back to the watcher, which
  now also covers the Google TV, Android TV and Leanback launchers.
- Control Center on Android TV opens the standard Settings pages (Wi-Fi, Sound, Accessibility, Bluetooth or
  Remotes & Accessories, Settings). Parental is hidden there; Switch Profile opens accounts and profiles where the
  TV has that page.
- Settings pages opened from GlassOS on Android TV get their own task, so Home always returns to GlassOS.
- Setup's debugging instructions and connection errors are written per platform, including Android 11 wireless
  debugging, which does not open port 5555.
- Footer and About: not affiliated with Google, Amazon or Apple; trademark notices for Android TV, Google TV and
  Fire TV.

## 1.0.0-beta (2026-10-22)

First public release.

- tvOS-inspired home for Fire TV: rotating Top Shelf, glass shelf of up to six apps with a peeking app drawer,
  adaptive profile pill, Control Center, comes back after Home, starts at boot.
- Top Shelf sources: TMDB (default, prefers your installed streaming services in your country), Apple TV
  (unofficial, off by default) and built-in scenes.
- First-run setup: name with an on-screen keyboard, profile photo or the glass avatar, country, top-row apps.
- One-tap permission setup through the TV's own ADB, with manual commands documented.
