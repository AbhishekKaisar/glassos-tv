# Changelog

## Unreleased

- Fire TV: after Home, the focus sound and a tile's focus animation no longer repeat on their own every few seconds,
  and the first top row app no longer stays enlarged for a moment. Package broadcasts that change nothing shown now
  refresh the tiles in place instead of rebuilding them (and no longer cancel an app being moved), and only a key
  press plays the focus sound (#16).
- Edit top row: an app being moved no longer snaps back when Fire OS sends a package broadcast that changes nothing
  shown; the move carries on where it is (#23).
- A package broadcast no longer throws away the home GlassOS prepares for the Home button: it takes the new app list
  and stays ready, so Home covers the stock home as fast after app updates as before them (#24).
- Fire TV setup: any refusal from the TV's own ADB other than debugging being off now points to the grants from a
  computer, like a closed connection, instead of "turn debugging off and on" (late Fire OS 6 refuses differently).
  On Fire OS 5 and 6 it names their notification access command, and the next visit leads with the computer steps
  until Fire OS is updated (#20).
- Android TV: when GlassOS is the default home app, Settings shows "Replace Android TV home" dimmed as "Not needed"
  and focus passes over it, instead of reading On for a watcher that does not run (#2).
- Setup: the welcome logo is sharp. It was the 96 px launcher icon stretched to 240 px; it now comes from a vector
  source of the same design (docs/logo.svg), rendered at 480 px. The launcher icon itself is unchanged (#1).
- Shorter first-run setup, like the Apple TV's: welcome, name, top row, permissions (and the home screen step on
  Android TV), then done. The country follows the TV's region and the profile photo is optional, so both moved to
  Settings, which now shows the country in use instead of "Not set". The last step no longer says a long press adds
  an app to the top row; it says to hold OK to move it.
- TVs with no device admin at all, such as the Nvidia Shield, no longer list Device admin as a permission that cannot
  be granted, and Control Center leaves out Sleep and the sleep timer there instead of failing. The README and
  AGENTS.md note that the Shield turns its stock launcher back on at every restart (#31).
- GlassOS no longer prepares the Home overlay when the home watcher cannot run, for example when it is the default
  home app: that prepared home was never shown, and it held a full home's views in memory while other apps ran.
- A small shield shows before the time in the profile pill while a VPN is connected, so you can see it without
  opening Control Center (#26).
- Top Shelf: two more sources in Settings > Top Shelf, TMDB trending today and TMDB trending this week, which show
  TMDB's trending titles whatever service they are on. Every title source now starts in a new order each time
  GlassOS starts (#29).
- README: the install section names the current APK and gives its direct link to type into Downloader; GitHub's
  `releases/latest` link does not work for pre-releases (#21).

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
