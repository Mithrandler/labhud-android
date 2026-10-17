# Changelog

All notable changes to LabHUD for Android. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **A clear "Offline" banner** while LabHUD cannot be reached: why, in words (no answer, refused,
  unknown name, another certificate), when the data was last live and when the next try is, with
  Retry now. It stays up through the retries instead of flashing, and the cards are dimmed so
  the last numbers are not taken for live ones. The top bar says "Offline" too.
- **An agent's ticks in the app**: Server management > an agent shows each capability it found
  with a switch, and the containers, guests, services or machines it may act on, each with a
  tick, as ⚙ > Agents does (the wall's PIN or the fingerprint first).

### Fixed
- A card's numbers were shown twice on its screen (labhud's own "numbers" section).
- The notification permission is checked before a notification is shown (Android 13+).

## [0.3.11] - 2026-10-16

### Added
- **Swipe left and right between the wall's pages** on Status, as on the wall; the chips follow.

## [0.3.0] - 2026-10-16

Needs labhud 0.3.0: the same version for the server, the agent and the app.

### Added
- **A new bottom bar: Status, Devices, Activity, Settings.**
  - **Status**: the wall's pages as chips, each group as a grid of small cards (two to a row on a
    phone, more on a tablet) with the dot, the name, two numbers and 24 hours of uptime.
  - **The bell** in the top bar: labhud's alerts (the rules that fired, since when) and the red cards.
  - **Games** as a page of its own: a tile per game server, then its players, address, RAM,
    uptime and the start / stop / restart buttons.
  - **Devices**: every machine (Proxmox, NAS, Docker hosts, VMs, PCs), with All / Running /
    Stopped / Problems.
  - **Activity**: the timeline and the notifications labhud sent.
- **Signing in as the admin** in Settings > Account. A paired phone only looks until then. The
  buttons are on the card's screen only, and only for the admin; with the wall's PIN they ask for
  it once (the buttons stay open for 2 minutes), and **a fingerprint can stand in for the PIN**:
  the PIN is then kept behind a key in the Android Keystore that only the fingerprint opens.
- **Containers** on a Docker host's card: their state, update available, Update, Restart, Stop,
  Start, and the last lines of the log (the admin).
- **The host's own charts** from its agent (CPU, RAM, disk, temperatures, network).
- **Server management** for the admin: the paired phones (Remove), the agents, and where labhud
  sends its news (ntfy, the app, both) with a test to ntfy.
- **Instant notifications** without Google in the app (Settings > Notifications): through
  UnifiedPush, with the ntfy app as the distributor (its Play version rides on Google's push).
  labhud only knocks, with no news in the knock; the app then reads the news itself, at home or
  on the VPN, and shows it. Or **ntfy only**, as before, or **off**.
- Signing in without a QR code works over HTTPS (labhud's default since 0.3): the certificate
  seen the first time is the only one accepted from then on. An address typed without
  http:// or https:// tries HTTPS first.

### Removed
- **Overview** and **Actions**: what they showed is now in Status, the bell, Devices and each
  card's screen ("Resources over time" opens a host's history).

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
