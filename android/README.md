# Island — the Android app

The same idea as the web demo, but as a real APK: a floating pill that sits at the
top of the screen over every app on the Pixel 7 Pro.

## What it does

- **Idle pill** — a 126×36 dp black pill under the status bar.
- **Music** — while a media session is playing (Spotify, YouTube Music, …) the pill
  shows the track; tap it to expand artwork, title, progress and
  prev / play-pause / next. Works through `MediaSessionManager`, so no hacky
  notification scraping.
- **Messages** — with notification access, incoming notifications pop in for 5 s
  with the app icon, sender and text; tapping opens the notification's own intent.
- **Timer** — *Try a live activity → Timer* starts a 5-minute countdown; tap the
  pill to pause / resume / cancel. It keeps counting while the island shows
  something else and fires a "Timer finished" state + vibration.
- **Idle dashboard** — the empty pill now rotates live widgets every 8 s: clock,
  battery, next alarm. **Tap the pill** and it opens into a mini dashboard with
  the time and date, battery/charging, next alarm, what is playing and any
  running timer, plus *Open / Timer / Charge* actions. The alarm chip opens the
  clock app, the media chip expands playback, the timer chip expands the timer.
- **Call screen** — tapping the name row on the expanded call card routes into a
  full-screen call UI (avatar, name, live timer, waveform, Mute / Route / End).
  The island has exactly one destination, like the iOS `widgetURL` route.
- **Multi-tasking gestures** — the pill takes real swipes: **left/right** cycles
  between concurrent activities (call → timer → live), **up** collapses,
  **down** expands. Small dots show how many activities are alive, and the
  expanded call card grows ‹ › arrows when there is something to switch to.
- **Call** — *Call* in the app starts a call live activity: compact shows a live
  waveform, the contact name and the running duration; tapping expands to the
  call card with **Mute**, **Speaker/route** and **End**. End shows “Call ended”
  for 2.5 s. Audio routing and the call itself are simulated — this is the island
  UX layer, ready to be wired to a real `ConnectionService`.
- **Charging** — plugging in springs the island open into a card with a pulsing
  bolt, the percentage counting up and a green fill bar; it then collapses to a
  compact pill and fades out.
- **Long-press** the pill to jump back to the settings screen.
- **Drag** the pill up or down to place it exactly where you want; the offset is
  saved in `SharedPreferences` and restored on the next start
  (*Reset pill position* restores the automatic spot).

## Build

No Gradle: `aapt2` + `javac` + `d8` + `apksigner`, same pipeline as the other
projects here.

```bash
cd android
./build.sh          # → island.apk
```

Requirements: `ANDROID_SDK` (default `/root/opt/android/sdk`) with
build-tools 34.0.0 and platforms/android-34.

## Install

```bash
cp island.apk /sdcard/Download/DSH-Go/island.apk
```

Then tap the APK in Files → Install. `pm install` does not work from the proot
shell here — it runs as the app's uid, not as shell, so the package installer UI
is the only route.

## Permissions

| Permission | Why | How |
| --- | --- | --- |
| Display over other apps | fallback overlay when accessibility is off | Allow → system toggle |
| Accessibility service | owns the window as `TYPE_ACCESSIBILITY_OVERLAY`, which is the *only* way to receive taps while the pill sits on the camera | Enable → Accessibility → Island |
| Notification access | messages + media session list | Enable → toggle "Island" |
| Notifications (13+) | the required foreground-service notification | asked on first launch |

Without overlay permission nothing can float; the app explains this and bounces
you to the right settings page.

## Architecture

| File | Role |
| --- | --- |
| `MainActivity` | setup screen, permission state, start/stop, demo triggers |
| `IslandService` | foreground service (`specialUse`), owns the controller |
| `IslandController` | overlay window, state machine, morph animation, drag/position, media/battery/timer |
| `NotifListener` | `NotificationListenerService`, feeds notifications + media token |
| `IslandA11yService` | `AccessibilityService` that hosts the overlay above the status bar |

The overlay is a single `TYPE_APPLICATION_OVERLAY` window whose width, height and
corner radius are animated with a `ValueAnimator`; content swaps between a
compact bar and an expanded card at the same time, so there is no second window
and no jump.

## Notes / limits

- The pill sits *below* the status bar (an app overlay is layered under the
  system UI window), so it reads as a floating pill rather than wrapping the
  punch-hole camera. The default offset uses the real `WindowInsets` status-bar +
  cutout height, with a 46 dp floor, because `status_bar_height` alone came back
  too small on the Pixel and left the pill half-hidden.
- Position is persisted per device; after a rotation the saved pixel offset is
  clamped back into the screen.
- Media only appears while something is actually playing.
- Notification filtering skips ongoing/group-summary/media notifications and the
  system UI itself; other apps' spam still shows (that is the point).

## Why an accessibility service

An app overlay (`TYPE_APPLICATION_OVERLAY`) is layered **below** the status bar.
The status bar is a system window and owns every touch in its height (41 dp on
this Pixel), so a pill drawn around the camera could be *seen* but never
*pressed* — taps went to SystemUI.

`TYPE_ACCESSIBILITY_OVERLAY` windows are layered **above** the status bar, so the
pill can wrap the front camera and still receive its own taps. That is why the
app ships a tiny `AccessibilityService` (`IslandA11yService`, no window content
access, description only) and switches the window to that type when it is
enabled. With accessibility off, it falls back to the application overlay just
below the bar, where taps work too.

## Why the pill was stuck below the status bar

Two separate causes, both fixed in 1.7/1.8:

1. An overlay window is laid out inside the *content* area by default, so its
   `y` origin is the bottom of the status bar. `y = 6 dp` therefore meant "6 dp
   below the status bar", never around the camera. Fixed with
   `WindowManager.LayoutParams.setFitInsetsTypes(0)` (API 30+) plus
   `FLAG_LAYOUT_IN_SCREEN` and `layoutInDisplayCutoutMode = ALWAYS`.
2. The default offset was derived from `status_bar_height`/insets (41 dp on this
   device), which is below the camera. The default is now **2 dp from the
   physical top**, which wraps the punch-hole, with the old behaviour available
   as the *Below bar* preset.

`DisplayCutout.getBoundingRects()` comes back empty for the activity on this
build, so the pill uses the 2 dp default rather than centring on a hole rect.

## Changelog

- **3.7** — animated RGB ring drawn on the capsule border (SweepGradient, 30 fps,
  follows the corner-radius morph, never consumes touches).
- **3.6** — foreground-service notification minimised: `IMPORTANCE_MIN`, no sound,
  no badge, hidden on the lock screen.
- **3.5** — tap on the idle capsule opens the quick panel (no clock); opening the
  app goes through a `PendingIntent` with
  `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` because Android blocks background
  activity starts.
- **3.4** — tap on idle tries to open the app, haptic tick on every tap.
- **3.3** — removed the automatic idle clock rotation: the empty capsule stays
  empty, long-press opens the dashboard.
- **3.2** — secondary activity bubble (tap to swap focus), long-press expands,
  accents from the system Material You colour.
- **3.1** — left/right edge anchoring presets for punch-hole / secondary-display
  layouts; presets re-lock the position.
- **3.0** — fixed camera position (6 dp, clear of the status-bar pull-down strip),
  *Lock position* switch on by default so the pill cannot drift, presets re-lock.
- **2.9** — tap detection reworked around the system touch slop; in-app tap/swipe
  counters for diagnostics.
- **2.8** — diagnostics line (`status bar · cutout · taps · swipes`).
- **2.7** — full-screen call screen reached by a single tap on the expanded call
  card; shared mute / route / end state with the island.
- **2.6** — swipe paging between concurrent activities, multi-task dots,
  expand/collapse swipes, switcher arrows on the call card.
- **2.5** — call live activity: animated waveform, duration timer, expandable
  Mute / Speaker / End card, "Call ended" state.
- **2.4** — live activities for any app's progress/ongoing notification.
- **2.3** — idle widgets rotate in the pill (clock / battery / alarm); tapping
  opened a live dashboard (time, battery, alarm, media, timer, quick actions).
  Removed again in 3.3.
- **2.2** — Quick Settings tile, boot auto-start, in-app shortcut settings link.
- **2.1** — accessibility overlay: the pill can sit around the front camera and
  still be tapped (falls back to just below the bar when accessibility is off).
- **2.0** — temporarily defaulted below the status bar because app overlays cannot
  receive taps in the bar.
- **1.9** — tap-to-access quick panel (Open / Timer / Charge) and the iOS-style
  charging animation: overshoot spring, pulsing bolt, count-up percentage, green
  fill, collapse to compact + fade.
- **1.8** — default position wraps the front camera (2 dp from the top edge).
- **1.7** — overlay window can now extend into the status bar
  (`setFitInsetsTypes(0)`, cutout mode `ALWAYS`).
- **1.6** — tried to centre on the cutout rect, added *Around camera* preset.
- **1.5** — live position slider in the settings screen.
- **1.4** — position presets (around camera / below bar / auto).
- **1.2** — drag-to-position (persisted) + *Reset pill position*; default offset
  from real window insets with a 46 dp floor; hairline edge so the pill is
  visible on dark apps.
- **1.1** — fixed the pill being half hidden under the status bar.
- **1.0** — first build.
