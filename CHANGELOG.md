# Changelog

All notable changes to LabHUD for Android. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.23] - 2026-10-22

Needs labhud 0.1.12 for the admin's name, the sessions list and the glance view; with an older server the app works as before (those parts stay hidden or say so).

### Added
- **A card's screen as tabs** (Overview, VM/LXC, Containers, GPUs, Storage, Backup, Updates, Notes, Links, History): one short screen each, swipe or tap between them, no transitions; VM/LXC, containers, storage and backup are mini-cards 2 to a row (3-4 on a tablet or in landscape). A container's buttons are behind a tap on its mini-card. A VM has a **Host** button, and Devices puts each VM/LXC under the host it runs on.
- **Uptime for 7 days** as a square a day with its percent.
- **Screen schedule** in Wall mode (Settings > Wall mode): dims the wall to a chosen brightness between two times (it may cross midnight); a touch wakes it for a minute, a red card for as long as it is red. Done in the app, nothing changes on the server.
- **Add / Ignore on "discovered" notifications** (Activity > Notifications): the buttons the server sends in `actions`, answered through the discovery calls (admin with the PIN typed; anything else says so). Only `/api/discover/*` paths are accepted.
- Hosts and Containers in Settings: the list of hosts is a foldable panel with a one-line summary.
- **Account** (Settings, first, also when only looking): who is signed in, Sign in / Sign out / single sign-on, **Lock now**, the admin's **Sessions** (sign one out, or every browser) and **Paired phones** (Remove), as panels that open on a tap.
- **Wall mode keeps your unlock**: the PIN you unlocked with in the app is handed to the wall page, so it does not ask again while the ticket lives (the same 2 minutes).
- **Glance view** in Wall mode (on by default, a switch in Settings > Wall mode): `?glance=1`, big coloured tiles with one number each.

### Changed
- Settings groups are Account, Board, Infrastructure, Alerts, System and This phone; **Phones** is no longer under System (it is Account > Paired phones, and still found by the search).
- A refusal ("forbidden") now says what to do: sign in as the admin, or unlock with the PIN. **Containers** no longer says "No agent runs Docker yet" when LabHUD refused the list; it reads the rights again.
- The red "N problems" band over the cards is gone: problems are in the bell, which keeps its badge.

## [0.1.22] - 2026-10-22

Needs labhud 0.1.11 for the new palette entries; with an older server the app works as before.

### Added
- **Palette**: the search also finds the containers the hosts' agents report (opening the host that lists
  them), with their buttons for the admin, the `[[bookmark]]` links, and a last entry that searches the web
  with the server's `[search] url`.
- **Stacks from git**: a stack deploy can take its compose file from a git repository (repo, branch, path;
  a token for a private one is used once and never saved). The server fetches it, so no new agent is needed.

## [0.1.21] - 2026-10-22

Needs labhud 0.1.1 (and labhud-agent 0.1.1 on a host for its Docker panel, stacks and live log).

### Added
- **Command palette**: the search (🔍) finds pages, cards, the cards' buttons (`restart · web`) and the
  sections of Settings, best match first, without regard to diacritics; `>` looks only among the commands
  and Enter runs the first hit. The buttons and Settings show only to the admin, and a button opens its
  card and asks there, behind the PIN, as a press on it does.
- **The host's Docker**: an agent's screen lists its images, volumes and networks with their sizes and
  users, and cleans each kind on its own (unused images, untagged layers, unused networks, anonymous volumes).
- **Stacks**: an agent's compose stacks with Edit, Redeploy, Restart and Down, and a new stack from a
  compose file and a `.env` (whose values are never read back).
- **Live log**: a container's log, followed as it is written.
- **Discovered, to approve**: Settings > Dashboard lists what labhud found with no card (Proxmox guests,
  containers, services from the scan), to add or dismiss, as on the wall.
- **Small widgets**: clock (with its own time zone), calendar, and a web page inside a card (it stays on
  its own host and loads no file).
- Icons on list rows.
- The HUD theme follows the board's theme (base oled or grey, accent a name or `#rrggbb`), set in the web
  ⚙ > Dashboard > Theme; the System, Dark and Light themes keep the phone's own colours.
- The app reads the `role` the server gives and shows the pages that role is allowed (`[roles.*]`).
- Icons: containers, games and the page chips show their icon.
- Settings > About: the version of the app and of the server, and "Update available" when GitHub
  has a newer release of the app, the server or the agent. The app sends `X-Labhud-App` so labhud
  knows which version asks.
- Themes: System, Dark, Light and HUD (the default), built on Material 3.
- Activity > Notifications: a Clear button, per phone and per server.

### Changed
- In the System, Dark and Light themes: large top bars on Settings, Activity and Devices that fold as
  the list scrolls, and raised cards without an edge (Material 3). HUD stays as the wall.
- A faster app: a state that has not changed redraws nothing, the connection banner and the charts
  read what they draw themselves, the line charts build their paths once, a finger on a chart
  redraws only the chart, the sorting and grouping of Devices and Activity are remembered, and
  one HTTP client per server is reused. The state goes to the disk once a minute (and when the
  app stops), a card asks for the history of its own series only (labhud with `?keys=`), wall mode
  stops the app's own stream while it is open, and a baseline profile is shipped.
- A card's containers are one panel per stack.

### Removed
- Leftovers of the 0.1.x packages (the "soon" badge, unused code and imports).

## [0.1.2] - 2026-10-21

Needs labhud 0.1.0.

### Added
- A container's CVEs (its image's last scan, critical and high) on its row and on the Updates list.

## [0.1.1] - 2026-10-21

Needs labhud 0.1.0.

### Added
- Settings › Containers: the registries with their own sign-in (add, edit, remove; the token kept in
  secrets.env), the image scans worst first, which agents can scan, and holding back an update whose
  image brings new critical CVEs.
- Settings › Notifications: email, webhook, Discord and Gotify, each with Set, a test and Forget, and
  which news goes where (a chip per channel on each kind of news).
- Settings › System: Backup and restore (one now, with the secrets encrypted; the daily one; the kept
  ones with Save to the phone, Restore and Delete) and the Danger zone (Remove every phone, New keys),
  each behind its typed word. Uploading a backup back stays on the wall.

### Fixed
- The versionCode starts at 1000000 (0.1.1 is 1000101), above the old numbering, so the app
  installs over an earlier build (0.3.15 was 315; the 0.1.0 APK, 100, had to replace it by hand).

## [0.1.0] - 2026-10-21

Needs labhud 0.1.0: the same version for the server, the agent and the app.

### Added

- **Single sign-on** in Settings > Security: this phone signs in as the admin through the browser
  (Authentik or any OpenID Connect provider); the password and the authenticator stay as the way
  back in.
- **The session's length and the PIN's idle time** in Settings > Security, and the networks the
  admin may sign in from.
- **API tokens and the audit log** in Settings > API and audit: a token is made, shown once and
  revoked; the log says who did what, when and from where.
- **Containers by compose stack**, with **Restart stack** when every running one of it has
  RESTART; each container's CPU and RAM and its latest update or crash under its name; **Details**
  beside Logs (ports, volumes, networks, the environment by name only). labhud 0.1.0 / agent 0.1.0.
- **Charts over 30 days and a year**, beside 6h, 24h and 7d.
- **Notes** (a `notes = true` card: read, and written by the admin), **links** (a `bookmarks`
  card) and **the fleet's updates** (`list = "fleet.updates"`: Update on each, Update all).
- **Search** in the top bar: every card by its name, line or page.
- In Server management, under Docker: **Update on their own** (the containers labhud updates by
  itself) and **Clean unused images**.
- The new icons (search, note, link) from labhud's design.
- **Wake-on-LAN by a typed MAC**: an agent's Wake-on-LAN list has a field for a machine that is
  off (and so not seen on the network), as ⚙ and the `labhud` command have.
- **Update an agent** from its screen in Server management, when labhud pins a newer release
  (labhud 0.1.0 or newer); it downloads and checks the release itself and starts again.
- **A clear "Offline" banner** while LabHUD cannot be reached: why, in words (no answer, refused,
  unknown name, another certificate), when the data was last live and when the next try is, with
  Retry now. It stays up through the retries instead of flashing, and the cards are dimmed so
  the last numbers are not taken for live ones. The top bar says "Offline" too.
- **An agent's ticks in the app**: Server management > an agent shows each capability it found
  with a switch, and the containers, guests, services or machines it may act on, each with a
  tick, as ⚙ > Agents does (the wall's PIN or the fingerprint first).
- **Swipe left and right between the wall's pages** on Status, as on the wall; the chips follow.
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
- **Overview** as the first page of Home: problems, then **resources** (CPU, RAM, GPU,
  temperatures, storage) per host with the last hours as small lines, **uptime** per card as
  status-page bars over 24 hours or 7 days, and the **latest events**.
- **A host's history**: tap its tile for big charts over 6 hours, 24 hours or 7 days; a finger on a
  chart reads the value and the time, with min / average / max under it.
- **Swipe between pages** on Home, as on the wall; the tabs follow.
- **Actions** shown as round buttons with icons (start, wake, reboot, shut down, update…), a
  filter per page, and each press followed in its card: sending, starting…, done or failed.
- A bottom bar: **Home**, **Today**, **Actions** and **Settings**. Wall mode and Lock moved to
  the ⋮ menu on Home.
- **Actions**: every button labhud offers (VMs, containers, games, reports) in one list, with the
  same confirmation as on the card. When the server takes no actions from phones it says what is
  missing (signed actions, LABHUD_ACTION_SECRET).
- **Sign in as the admin** when adding a server: address, user, password and the authenticator
  code pair the phone without a QR code (labhud 0.1.0 or newer).
- Pairing with a QR code (or the link under it) from labhud 0.1.0: ⚙ > Phone app, or `init.py pair`.
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

### Changed

- **The wall's icons and colours.** Thin-line icons, the same as on the wall and in ⚙, instead of
  Android's filled ones; the colours and icons come from labhud's `design/tokens.json`
  (`ui/Tokens.kt` is written by its `design/build.py`, not by hand). The Material icons library is gone.
- **The lock is a choice**, in Settings: none (the default), the phone's PIN / pattern / password,
  or a fingerprint (with the screen lock as the way out). Changing it asks once. Coming from an earlier version:
  the lock stays on (screen lock) only if you had switched it on yourself; the old default becomes none.
- The big "All good" card is gone: a dot and the time of the data sit in the top bar, and a thin
  red line names the problems only while there are some.
Needs labhud 0.1.0 for uptime and for telling a temperature from a share; with an older server
those parts are simply not shown.

### Removed

- **Overview** and **Actions**: what they showed is now in Status, the bell, Devices and each
  card's screen ("Resources over time" opens a host's history).

### Fixed

- The note in Settings > Notifications read the admin's rights without observing them, so it did
  not follow a sign-in.
- **"Allow the camera" closed the app** instead of asking for the camera, and so did asking for
  the notification permission in Settings: the fingerprint library brought an old part of Android
  that refused the request. The app now asks, and a "Don't allow" leaves it open with the button.
- A card's numbers were shown twice on its screen (labhud's own "numbers" section).
- The notification permission is checked before a notification is shown (Android 13+).
- The QR scanner crashed when the camera permission was refused, given "only this time", taken
  back in the settings or the camera was switched off in Quick Settings. It now asks again, offers
  the system settings after a permanent refusal, and pasting the link or signing in always works.

[Unreleased]: https://github.com/Mithrandler/labhud-android/compare/v0.1.23...HEAD
[0.1.23]: https://github.com/Mithrandler/labhud-android/compare/v0.1.22...v0.1.23
[0.1.22]: https://github.com/Mithrandler/labhud-android/compare/v0.1.21...v0.1.22
[0.1.21]: https://github.com/Mithrandler/labhud-android/compare/v0.1.2...v0.1.21
[0.1.2]: https://github.com/Mithrandler/labhud-android/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Mithrandler/labhud-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Mithrandler/labhud-android/releases/tag/v0.1.0
