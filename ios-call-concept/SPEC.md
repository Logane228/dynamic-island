# Dynamic Island — Calls & Live Audio
### Interactive concept + technical specification (iOS 17/18, ActivityKit)

> Companion prototype: `index.html` in this folder — tap states in the dock, mute / change route / end, and the secondary Live Activity bubble.
> Run it at `http://127.0.0.1:8099/ios-call-concept/`.

---

## 1. Concept

A VoIP call lives in the Dynamic Island as a *live audio object*: the pill carries the
caller, a breathing waveform and the running duration; the expanded card gives
mute / route / end without ever leaving the current app; a single tap returns to
the call screen.

Three design rules drive everything:

1. **Glanceable, not readable.** In compact, the waveform and the ticking timer
   carry the state; the name is a secondary cue and truncates first.
2. **Controls are intents, not navigation.** Mute and route change the call in
   place (`LiveActivityIntent`), with haptics — they never open the app.
3. **One tap, one destination.** Any non-button tap resolves to the active call
   screen via `widgetURL`; there is exactly one route.

---

## 2. System constraints you must design inside

| Constraint | Value | Consequence |
| --- | --- | --- |
| Compact island | ≈126 × 37 pt, sensor gap in the middle | leading/trailing budget ≈ 50 pt each |
| Expanded island | up to ≈ 371 × 160 pt (393 pt screen) | 16 pt padding, ~44 pt corner radius |
| Minimal | ~37 pt circle | icon or one glyph only |
| Touch target | 44 × 44 pt minimum (HIG) | 56 pt controls in practice |
| Live Activity animation | **no free-running animations** | waveform = discrete state updates + spring transitions |
| Update budget | a few per second, coalesced | 6 Hz waveform max, timers use `Text(timerInterval:)` |
| Payload | ~4 KB (push), small for local too | send 5 levels, not an audio buffer |
| Gestures | tap + system long-press only | no swipes/custom gestures in the island |

Two consequences worth stating up front:

- The waveform is **not** a `TimelineView` animation inside the widget. Widget
  views are snapshots; you push 5 normalised levels ~6×/s and let SwiftUI spring
  between them.
- The timer is **not** a `Timer`/`onReceive`. `Text(timerInterval:countsDown:)`
  ticks in the system process, costs zero updates and survives your app being
  suspended.

---

## 3. State model

```
connecting ──▶ active ──▶ ended ──▶ (dismiss)
                 │  ▲
      long-press │  │ tap outside / collapse
                 ▼  │
             expanded (mic / route / end)
```

| State | Island size | Content | Entry |
| --- | --- | --- | --- |
| `connecting` | compact | spinner + “Calling…” | activity request |
| `active` | compact | waveform + name + timer | call connected |
| `expanded` | expanded | avatar, name, route · timer, 3 controls | system long-press (or programmatic expand) |
| `ended` | compact | “Call ended · 04:12” | end intent |
| `idle` | — | — | activity ended after 2.5 s |

Only `active` and `ended` are Live Activity states; `expanded` is a system
presentation of the same activity.

---

## 4. State 1 — Compact (background call)

```
┌──────────────────────────────────────────────┐
│  ▁▃▅▂   Camille…              04:12          │
└──────────────────────────────────────────────┘
   waveform   display name        live timer
   (≤ 20 pt)  (truncates first)   (monospaced)
```

- **Leading**: 4-bar waveform, 20 × 14 pt, 2 pt bars, 2 pt gaps, `#30D158`;
  muted → flat 2 pt bars at 25% opacity, colour `#8E8E93`.
- **Name**: 13 pt semibold, `lineLimit(1)`, `.truncationMode(.tail)`,
  `.minimumScaleFactor(0.85)`. Use a short `displayName` attribute (≤ 12 chars,
  first name) — never the full contact name.
- **Trailing**: timer, 13 pt `.monospacedDigit()`, width 44 pt, right aligned.
- **Live indicator**: 5 pt green dot before the waveform; grey when muted.
- **Accessibility label**: `"Call with Camille, 4 minutes 12 seconds, muted"` —
  the waveform is decorative (`accessibilityHidden(true)`).

**Truncation order when space is tight:** waveform shrinks to 3 bars → name
truncates → name is dropped (initials only) → minimal state.

---

## 5. State 2 — Expanded (long-press)

```
┌──────────────────────────────────────────────┐
│  (C)  Camille Laurent                04:12   │
│       iPhone · 04:12                         │
│                                              │
│      (mic)        (speaker)        (end)     │
│      Mute          Speaker          End      │
└──────────────────────────────────────────────┘
```

Layout, top to bottom (16 pt side padding):

| Region | Content | Size |
| --- | --- | --- |
| `.leading` | avatar 40 pt (initials fallback) | 40 × 40 |
| `.trailing` | timer 15 pt medium monospaced | 44 pt wide |
| header text | name 15 pt semibold; `route · duration` 12 pt secondary | 1 line each |
| `.bottom` | 3 controls, 56 pt circles, 24–32 pt apart, labels 11 pt | 56 × 56 + 14 |

Rules:

- **Mute** — toggles in place, icon `mic.slash.fill`, circle becomes white with a
  black glyph; label becomes “Unmute”. Haptic: `.impact(style: .light)`.
- **Speaker / route** — cycles Receiver → Speaker → Bluetooth/AirPods. Label shows
  the *current* route; the icon stays `speaker.wave.2.fill`. Haptic: `.selection`.
- **End** — red `#FF453A`, `phone.down.fill`, rotated 135°; requires no
  confirmation; haptic: `.notificationOccurred(.warning)`.
- Buttons are **intents**, so the app never comes forward.
- Tapping anywhere that is *not* a button opens the call screen (same route as a
  single tap on compact).
- Expanded height stays ≤ 160 pt; do not add a fourth control — route selection
  belongs behind a long-press on the speaker button or in the app.

---

## 6. State 3 — Split / multi-tasking

iOS presents **one** Live Activity in the island at a time. Extra activities
surface as a compact bubble *below* the island (and stack on the Lock Screen).
The app cannot force which one is primary — the system promotes the most recently
started or updated activity.

Design rules:

1. **Cap at two activities** (call + one companion: timer, navigation, delivery).
2. **Keep the call primary** by updating it on meaningful events (connect, route
   change, mute) — not by spamming levels.
3. **Make the companion self-sufficient**: a timer activity should render
   `Text(timerInterval:)` so it needs zero updates and never competes.
4. **Secondary bubble spec**: 34 pt height, icon + label + value, black, 17 pt
   radius, 6 pt below the island; tap opens that activity's app.
5. If the companion must be promoted (e.g. navigation turn), let it update once;
   the call drops to the bubble and comes back on the next call event.
6. End stale activities aggressively (`dismissalPolicy: .after(date)`), otherwise
   the system stacks them.

In the prototype: *Split view* adds a Timer bubble under the island; the bubble
shows 12:48 and tapping it opens the Timer app.

---

## 7. State 4 — Motion & real-time updates

### 7.1 Choreography

| Moment | Animation |
| --- | --- |
| compact → expanded | system-driven container spring; content `.spring(response: 0.32, dampingFraction: 0.78)` |
| idle → calling | keyline tint pulse once, then steady |
| mute | waveform collapses to flat: `.spring(response: 0.28, dampingFraction: 0.7)` + cross-fade icon |
| end | card shrinks to compact, then fades out after 2.5 s (`dismissalPolicy: .after`) |
| levels | bars interpolate between snapshots: `.spring(response: 0.22, dampingFraction: 0.65)` |

Never animate width/height of the island yourself — the system owns the container.

### 7.2 Waveform pipeline

```
mic tap (AVAudioEngine) → RMS → attack/release smoothing → 5 buckets
   → throttle 6 Hz → Activity.update() → widget redraws bars
```

- Send **5 normalised values** `[Double]` in `ContentState.levels` (0…1), not a
  single level, so the bars keep a natural shape.
- Throttle to **6 Hz**; drop frames when the value delta < 0.02.
- Muted: stop updates entirely and set `isMuted = true` — the widget renders flat
  bars, saving the whole update budget.
- iOS may coalesce updates; design for 4–6 Hz, never 30.

### 7.3 Timer

```swift
Text(timerInterval: startedAt...Date.distantFuture, countsDown: false)
    .monospacedDigit()
```

Zero updates, always accurate, survives suspension. Never send `elapsed` on a
timer.

---

## 8. State 5 — Single-tap routing

- Every island surface carries
  `.widgetURL(URL(string: "callapp://call/\(callID)"))`.
- A tap on compact, or on any non-button area of the expanded card, launches the
  app with that URL.
- The app resolves it in one place:

```swift
.onOpenURL { url in
    guard url.scheme == "callapp", url.host == "call" else { return }
    router.openActiveCall()          // push CallScreen, not a new root
}
```

- Cold start: `scenePhase == .active` + pending URL → set the navigation path
  before the first frame, so the call screen is the root (no visible push).
- Warm start: `navigationPath.append(.call(id))`, replacing any modal.
- Buttons use `Button(intent:)`, which never triggers `widgetURL`.

---

## 9. Implementation

### 9.1 Attributes

```swift
import ActivityKit
import Foundation

struct CallAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var startedAt: Date
        var isMuted: Bool = false
        var route: AudioRoute = .receiver
        var phase: CallPhase = .connecting
        var levels: [Double] = [0.2, 0.4, 0.3, 0.5, 0.25]   // 5 buckets, 0…1
    }
    var callID: UUID
    var displayName: String        // ≤ 12 chars, first name
    var fullName: String
    var avatarURL: URL?
}

enum AudioRoute: String, Codable { case receiver, speaker, headphones, bluetooth }
enum CallPhase: String, Codable { case connecting, active, ended }
```

Start / end:

```swift
let activity = try Activity<CallAttributes>.request(
    attributes: .init(callID: id, displayName: "Camille", fullName: "Camille Laurent"),
    content: .init(state: .init(startedAt: .now, phase: .connecting), staleDate: nil),
    pushType: nil
)

await activity.end(nil, dismissalPolicy: .after(.now + 2.5))
```

### 9.2 Widget

```swift
struct CallLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CallAttributes.self) { context in
            LockScreenCallView(context: context)
                .activityBackgroundTint(.black)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Avatar(url: context.attributes.avatarURL,
                           initials: context.attributes.displayName)
                        .frame(width: 40, height: 40)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    CallTimer(startedAt: context.state.startedAt)
                        .font(.system(size: 15, weight: .medium).monospacedDigit())
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.attributes.fullName)
                            .font(.system(size: 15, weight: .semibold))
                            .lineLimit(1)
                        Text("\(routeName(context.state.route)) · \(durationText(context.state))")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    CallControls(attributes: context.attributes, state: context.state)
                }
            } compactLeading: {
                HStack(spacing: 5) {
                    Waveform(levels: context.state.levels, muted: context.state.isMuted)
                        .frame(width: 20, height: 14)
                    Text(context.attributes.displayName)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
            } compactTrailing: {
                CallTimer(startedAt: context.state.startedAt)
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .frame(width: 44, alignment: .trailing)
            } minimal: {
                Image(systemName: context.state.isMuted ? "mic.slash.fill" : "phone.fill")
                    .foregroundStyle(.green)
            }
            .keylineTint(.green)
            .widgetURL(URL(string: "callapp://call/\(context.attributes.callID.uuidString)"))
        }
    }
}

struct CallTimer: View {
    let startedAt: Date
    var body: some View {
        Text(timerInterval: startedAt...Date.distantFuture, countsDown: false)
            .monospacedDigit()
            .lineLimit(1)
    }
}
```

### 9.3 Waveform (state-driven, not free-running)

```swift
struct Waveform: View {
    var levels: [Double]
    var muted: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 2) {
            ForEach(0..<min(levels.count, 5), id: \.self) { i in
                Capsule(style: .continuous)
                    .fill(muted ? Color.gray.opacity(0.35) : Color.green)
                    .frame(width: 2,
                           height: muted ? 2 : max(3, CGFloat(levels[i]) * 14))
            }
        }
        .frame(maxHeight: 14, alignment: .center)
        .animation(.spring(response: 0.22, dampingFraction: 0.65), value: levels)
        .animation(.spring(response: 0.28, dampingFraction: 0.7), value: muted)
        .accessibilityHidden(true)
    }
}
```

### 9.4 Controls as intents (no app launch)

```swift
struct CallControls: View {
    let attributes: CallAttributes
    let state: CallAttributes.ContentState

    var body: some View {
        HStack(spacing: 26) {
            Button(intent: ToggleMuteIntent(callID: attributes.callID.uuidString)) {
                ControlLabel(system: state.isMuted ? "mic.slash.fill" : "mic.fill",
                             title: state.isMuted ? "Unmute" : "Mute",
                             active: state.isMuted)
            }
            .buttonStyle(.plain)

            Button(intent: CycleRouteIntent(callID: attributes.callID.uuidString)) {
                ControlLabel(system: "speaker.wave.2.fill",
                             title: routeName(state.route))
            }
            .buttonStyle(.plain)

            Button(intent: EndCallIntent(callID: attributes.callID.uuidString)) {
                ControlLabel(system: "phone.down.fill", title: "End", destructive: true)
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 10)
    }
}

struct ToggleMuteIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Toggle mute"
    @Parameter(title: "Call ID") var callID: String

    func perform() async throws -> some IntentResult {
        await CallCenter.shared.toggleMute(callID: UUID(uuidString: callID)!)
        return .result()
    }
}

struct EndCallIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "End call"
    @Parameter(title: "Call ID") var callID: String

    func perform() async throws -> some IntentResult {
        await CallCenter.shared.end(callID: UUID(uuidString: callID)!)
        return .result()
    }
}
```

`CallCenter` performs the CallKit action (`CXSetMutedCallAction`,
`CXEndCallAction`) so the system call state, audio route and the island stay in
sync.

### 9.5 Audio metering → throttled updates

```swift
final class LevelMeter {
    private let engine = AVAudioEngine()
    private var smoothed: Double = 0
    private var lastSent = Date.distantPast

    func start(onLevels: @escaping ([Double]) -> Void) {
        let input = engine.inputNode
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buf, _ in
            let rms = Self.rms(buf)
            // attack fast, release slow
            self.smoothed = rms > self.smoothed
                ? self.smoothed + (rms - self.smoothed) * 0.6
                : self.smoothed * 0.85
            guard Date().timeIntervalSince(self.lastSent) > 1.0 / 6.0 else { return }
            self.lastSent = Date()
            onLevels(Self.buckets(from: self.smoothed))
        }
        try? engine.start()
    }

    /// one level → 5 slightly offset buckets so the bars keep a shape
    static func buckets(from level: Double) -> [Double] {
        let shape: [Double] = [0.55, 0.85, 1.0, 0.75, 0.6]
        return shape.map { min(1, level * $0 * 1.6) }
    }
}
```

```swift
func send(levels: [Double]) async {
    guard let activity = Activity<CallAttributes>.activities.first else { return }
    var state = activity.content.state
    state.levels = levels
    await activity.update(ActivityContent(state: state, staleDate: Date().addingTimeInterval(30)))
}
```

### 9.6 Routing

```swift
.onOpenURL { url in
    guard url.scheme == "callapp" else { return }
    if case .call = Router.destination(for: url) {
        callStore.presentActiveCall()      // single destination
    }
}
```

---

## 10. CallKit reality check

- For a **system phone call**, iOS owns the presentation (status bar / call UI).
  Do not duplicate it with a Live Activity.
- For **VoIP** (WhatsApp-style, FaceTime-like), use CallKit for the audio/call
  lifecycle and a Live Activity for the island. Keep CallKit’s banner and the
  island in agreement: mute/route from the island must go through
  `CXCallController`, never a local-only flag.
- FaceTime calls are system-owned; a third-party live activity cannot replace
  them.

---

## 11. Interaction rules (summary)

| Gesture / event | Result |
| --- | --- |
| Tap compact | Open call screen (`widgetURL`) |
| Long-press compact | System expands the card |
| Tap expanded (non-button) | Open call screen |
| Tap Mute | Intent → toggle, haptic, no app launch |
| Tap Speaker | Intent → next route, label updates |
| Tap End | Intent → CallKit end, island shows “Call ended”, dismisses 2.5 s later |
| Tap secondary bubble | Open that live activity’s app |
| Incoming call | New activity replaces the current one; don’t stack |
| Call ends from app/CallKit | `activity.end(...)`, no stale card |

---

## 12. QA checklist

- [ ] Name truncates before the waveform; never overlaps the sensor gap.
- [ ] Timer stays correct after backgrounding for 10 min (system timer).
- [ ] Muted state survives an app kill (state in activity, not in the view).
- [ ] Levels stop updating when muted (verify via Console: no `update` calls).
- [ ] End from the island and from the app both dismiss the activity.
- [ ] VoiceOver reads one sentence, not five bars.
- [ ] Reduce Motion: no spring overshoot, cross-fade only.
- [ ] Dark/light Lock Screen banner contrast ≥ 4.5:1.
- [ ] Long name (CJK/RTL) still fits; `displayName` capped at 12 chars.
- [ ] Two activities: call stays primary; timer never steals focus.

---

## 13. Prototype deviations

The web prototype in `index.html` is a design sandbox, not a system replica:

- Tap expands (on device, tap opens the app; expansion is a long-press).
- Waveform bars animate with CSS keyframes (on device they are state updates).
- The secondary bubble is static illustration (on device, system-managed).
