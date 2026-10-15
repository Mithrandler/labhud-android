# Changelog

All notable changes to LabHUD for Android. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.2] - 2026-10-15

### Added
- **Overview** as the first page of Home: problems, then **resources** (CPU, RAM, GPU,
  temperatures, storage) per host with the last hours as small lines, **uptime** per card as
  status-page bars over 24 hours or 7 days, and the **latest events**.
- **A host's history**: tap its tile for big charts over 6 hours, 24 hours or 7 days; a finger on a
  chart reads the value and the time, with min / average / max under it.
- **Swipe between pages** on Home, as on the wall; the tabs follow.
- **Actions** shown as round buttons with icons (start, wake, reboot, shut down, update…), a
  filter per page, and each press followed in its card: sending, starting…, done or failed.

### Changed
- **The lock is a choice**, in Settings: none (the default), the phone's PIN / pattern / password,
  or a fingerprint (with the screen lock as the way out). Changing it asks once. Coming from 0.1.1:
  the lock stays on (screen lock) only if you had switched it on yourself; the old default becomes none.
- The big "All good" card is gone: a dot and the time of the data sit in the top bar, and a thin
  red line names the problems only while there are some.

Needs labhud 0.2.15 for uptime and for telling a temperature from a share; with an older server
those parts are simply not shown.

## [0.1.1] - 2026-10-15

### Added
- A bottom bar: **Home**, **Today**, **Actions** and **Settings**. Wall mode and Lock moved to
  the ⋮ menu on Home.
- **Actions**: every button labhud offers (VMs, containers, games, reports) in one list, with the
  same confirmation as on the card. When the server takes no actions from phones it says what is
  missing (signed actions, LABHUD_ACTION_SECRET).
- **Sign in as the admin** when adding a server: address, user, password and the authenticator
  code pair the phone without a QR code (labhud 0.2.13 or newer).

### Fixed
- The QR scanner crashed when the camera permission was refused, given "only this time", taken
  back in the settings or the camera was switched off in Quick Settings. It now asks again, offers
  the system settings after a permanent refusal, and pasting the link or signing in always works.

## [0.1.0] - 2026-10-15

### Added
- Pairing with a QR code (or the link under it) from labhud 0.2.0: ⚙ > Phone app, or `init.py pair`.
  The token is kept encrypted with a key in the Android Keystore; HTTPS servers are pinned to the
  certificate named in the QR code.
- The fingerprint, face or phone PIN when the app opens, again after 5 minutes in the background.
- Home: all good or the number of problems, labhud's alerts, the problems, then every page as a tab
  with its groups and cards (Proxmox guests two to a row), live while the app is open.
- A card's screen: status and note, numbers, history charts over 6 hours, 24 hours or 7 days, its
  panel, links, actions (with labhud's questions and choices) and maintenance.
- The timeline, by day.
- Several servers, renamed or removed in Servers and settings.
- A home screen widget, refreshed every 15 minutes while it is placed.
- Wall mode: the wall page full screen and kept on; optionally the home screen app, so it comes
  back after a reboot.
- `labhud://card/<id>` links (ntfy's Click with `LABHUD_NOTIFY_CLICK=app`) open that card.

[Unreleased]: https://github.com/Mithrandler/labhud-android/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/Mithrandler/labhud-android/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Mithrandler/labhud-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Mithrandler/labhud-android/releases/tag/v0.1.0
