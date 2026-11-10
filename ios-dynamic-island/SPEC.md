# Dynamic Island — Gestures, Multi-tasking & ActivityKit
### Architecture + production code (iOS 17/18, Swift 5.10, WidgetKit + App Intents)

Source files: `Sources/`
- `IslandAttributes.swift` — payload model
- `IslandActivityManager.swift` — single owner of the activity
- `IslandLiveActivity.swift` — widget: regions, controls, lock screen
- `IslandIntents.swift` — `LiveActivityIntent`s
- `LevelMeter.swift` — mic RMS → 5 buckets @6 Hz
- `AppShell.swift` — router, home, call screen
- `IslandReplica.swift` — in-app gesture replica (tap / long-press / swipe)

---

## 1. Reality check: what the island actually lets you do

| Intent | Possible? | Mechanism |
| --- | --- | --- |
| Single tap → open app / route | **Yes** | `.widgetURL` on the island; app handles `onOpenURL` |
| Tap a control inside the island | **Yes** (iOS 17+) | `Button(intent:)` with `LiveActivityIntent` (runs in your app, no foregrounding) |
| Long press → expand | **System-owned** | You cannot add or intercept it. You pre-render the expanded regions; the system animates the container |
| Horizontal swipe inside the island | **No** | WidgetKit does not deliver drag gestures; `DragGesture`/`.onLongPressGesture` inside the widget are ignored |
| Collapse / resize programmatically | **No** | Only the user expands/collapses; you can `end()` the activity |
| Choose which activity is displayed | **No** | With several activities the system shows the most recently updated one |
| Two activities side-by-side in the island | **No** | One island slot |

**Design consequence.** You cannot ship a literal "swipe left/right to switch activities"
inside the island. The production pattern is:

1. **One activity, many tasks.** The call, timer and any transfer live in the same
   `ContentState`; switching tasks is a state update issued by in-island buttons —
   a *tappable* replacement for the swipe.
2. **In-app replica** (`IslandReplica`) implements the full gesture model with
   spring transitions for the call screen and design QA. That is where
   `.onTapGesture`, `.onLongPressGesture` and `DragGesture` actually run.
3. **Single tap routing** stays exactly as specified: any non-button tap launches
   `island://open?task=<id>` and lands on the call screen.

---

## 2. Architecture

```
┌───────────── app process ─────────────┐      ┌──── widget extension ────┐
│ CallController / TimerController      │      │ IslandLiveActivity       │
│        │ state changes                │      │  compact / expanded /    │
│        ▼                              │      │  minimal / lock screen   │
│ IslandActivityManager (MainActor)     │─────▶│                          │
│  • one Activity<IslandAttributes>     │update│  Button(intent:) ──┐     │
│  • task list + activeTaskID           │      │  .widgetURL ───────┼──┐  │
│  • throttled levels                   │      └────────────────────┼──┼──┘
│ LevelMeter (AVAudioEngine)            │                           │  │
└───────────────┬───────────────────────┘                           │  │
                │ LiveActivityIntent (runs here, no UI)             │  │
                ▼                                                   ▼  ▼
        IslandIntents.swift                                  island://open
                                                                    │
                                                          AppRouter.path
```

Rules:
- The manager is the **only** place that calls `Activity.request/update/end`.
- The widget never mutates local state; it only renders `context.state` and emits intents.
- Intents are the only mutation path from the island; the app is the only source of truth.

---

## 3. Gesture architecture

### 3.1 Single tap → full-screen routing

```swift
// IslandLiveActivity.swift
DynamicIsland { /* regions */ }
    .keylineTint(.green)
    .widgetURL(deepLink(for: context.state))

private func deepLink(for state: IslandAttributes.ContentState) -> URL? {
    var components = URLComponents()
    components.scheme = "island"
    components.host = "open"
    if let id = state.activeTask?.id {
        components.queryItems = [URLQueryItem(name: "task", value: id)]
    }
    return components.url
}
```

```swift
// AppShell.swift
.onOpenURL { router.handle($0) }

func handle(_ url: URL) {
    guard url.scheme == "island", url.host == "open" else { return }
    let task = URLComponents(url: url, resolvingAgainstBaseURL: false)?
        .queryItems?.first { $0.name == "task" }?.value
    path = [.call(taskID: task)]        // single destination, no stack growth
}
```

App launch from a cold start arrives at the same handler before the first frame,
so the call screen is the root — no visible push.

### 3.2 Long press → expansion

The system performs the expansion. There is **no callback**. Your contract is:
render the `.expanded` regions for the current state at all times, and keep them
cheap — they may be snapshotted the instant the user presses.

What you can control:
- expanded content only (avatar, title, clock, controls),
- the keyline tint,
- the state payload that the expanded layout reads.

What you cannot control: the spring of the container, the press threshold, or
whether a press collapses an open card.

### 3.3 Controls = intents (no app launch)

```swift
Button(intent: ToggleMuteIntent(taskID: task.id)) {
    ControlGlyph(systemName: task.isMuted ? "mic.slash.fill" : "mic.fill",
                 title: task.isMuted ? "Unmute" : "Mute",
                 active: task.isMuted)
}
```

`LiveActivityIntent.perform()` runs in the app's process without activating it,
so mute/route/end never leave the current app.

### 3.4 The gesture model you *can* build (in-app replica)

`IslandReplica` is the place for the literal gestures requested. Same spring
feel, same metrics:

```swift
.offset(x: dragX * 0.4)
.onTapGesture { toggle() }
.onLongPressGesture(minimumDuration: 0.35) {
    withAnimation(.spring(response: 0.4, dampingFraction: 0.78)) { mode = .expanded }
}
.gesture(
    DragGesture(minimumDistance: 10)
        .onChanged { value in
            guard abs(value.translation.width) > abs(value.translation.height) else { return }
            dragX = value.translation.width
        }
        .onEnded { value in
            let dx = value.translation.width
            withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) { dragX = 0 }
            guard abs(dx) > 56 else { return }
            withAnimation(.spring(response: 0.42, dampingFraction: 0.78)) {
                mode = dx < 0 ? .expanded : .compact
            }
        }
)
.animation(.spring(response: 0.42, dampingFraction: 0.78), value: mode)
.animation(.interactiveSpring(response: 0.3, dampingFraction: 0.82), value: dragX)
```

Metrics: compact 232 × 38, radius 19; expanded 340 × 188, radius 42; drag
resistivity 0.4; commit threshold 56 pt; expansion spring 0.42/0.78.

---

## 4. ActivityKit & regions

### 4.1 Attributes

`IslandAttributes.ContentState` carries an array of `IslandTask` plus
`activeTaskID` and five `levels`. Five tasks with short titles stay < 1 KB, far
under the ~4 KB budget. Timestamps are absolute (`startedAt`, `endsAt`) so the
widget never needs an "elapsed" update — `Text(timerInterval:)` handles seconds.

### 4.2 Configuration

```swift
ActivityConfiguration(for: IslandAttributes.self) { context in
    LockScreenIslandView(context: context)
        .activityBackgroundTint(.black)
} dynamicIsland: { context in
    DynamicIsland {
        DynamicIslandExpandedRegion(.leading)  { TaskAvatar(task: context.state.activeTask) }
        DynamicIslandExpandedRegion(.trailing) { TaskClock(task: ...) }
        DynamicIslandExpandedRegion(.center)   { TitleAndSubtitle(...) }
        DynamicIslandExpandedRegion(.bottom)   { ExpandedControls(state: context.state) }
    } compactLeading:  { CompactLeading(state: context.state) }
      compactTrailing: { CompactTrailing(state: context.state) }
      minimal:         { MinimalView(task: context.state.activeTask) }
}
```

### 4.3 Region budgets

| Region | Content | Budget |
| --- | --- | --- |
| compactLeading | task dots (if >1), waveform 18×14 or glyph, first name | ~50 pt |
| compactTrailing | clock / percent, 44 pt fixed, `.monospacedDigit()` | 44 pt |
| minimal | one SF Symbol, tinted | 20 pt |
| expanded .leading | avatar 38 | 38 |
| expanded .trailing | clock | 44 |
| expanded .center | title 15 semibold + subtitle 12 | flexible |
| expanded .bottom | task switcher (if >1) + controls 40 pt circles | ≤ 92 pt |

---

## 5. Multi-tasking state management

One activity is the source of truth for N tasks:

```swift
public struct ContentState: Codable, Hashable, Sendable {
    public var tasks: [IslandTask]
    public var activeTaskID: String?
    public var levels: [Double]
    public var updatedAt: Date
}
```

Focus rules:
- `activeTaskID` may point at any task; the compact and expanded regions both
  render `activeTask`.
- Switcher buttons call `FocusNextTaskIntent` / `FocusPreviousTaskIntent`, which
  rotate `activeTaskID` with a spring-animated page dot in the widget.
- Priority for *automatic* focus (app side): a call that becomes active takes
  focus; ending it falls back to the next task (`end(taskID:)` reassigns).
- Never create a second `Activity` for a companion task. If a second activity is
  unavoidable (e.g. another subsystem owns it), expect the system to show
  whichever updated last — keep updates on the call meaningful, not chatty.

---

## 6. Real-time updates

| Data | Cadence | Mechanism |
| --- | --- | --- |
| Call duration | 0 pushes | `Text(timerInterval:countsDown:false)` |
| Timer | 0 pushes | same, `startedAt` re-anchored on resume |
| Waveform | ≤ 6 Hz | `Activity.update` with 5 bucketed levels |
| Progress (delivery) | on change | `Activity.update` |
| Mute / route / focus | on action | intent → `update` |

```swift
func pushLevels(_ levels: [Double]) async {
    guard Date().timeIntervalSince(lastPush) > 0.16 else { return }
    guard let task = state?.activeTask, task.kind == .call, !task.isMuted else { return }
    lastPush = .now
    await update { $0.levels = levels }
}
```

Muted → no updates at all; the widget renders flat grey bars from `isMuted`.

---

## 7. Motion

- Container expansion: system-owned. Do not animate frame yourself.
- Content: `.animation(.spring(response: 0.28, dampingFraction: 0.8), value: state.activeTaskID)`.
- Waveform: `.spring(0.22, 0.65)` between level snapshots.
- Dots: width/opacity spring 0.28.
- Respect Reduce Motion: replace springs with `.easeOut(duration: 0.2)` in the
  app replica; the widget inherits the system's reduced-motion behaviour.

---

## 8. Production checklist

**Project**
- Widget extension target + `NSSupportsLiveActivities = YES` in Info.plist.
- `NSSupportsLiveActivitiesFrequentUpdates = YES` for the 6 Hz waveform.
- `NSMicrophoneUsageDescription`; audio background mode if metering while the
  call is backgrounded.
- URL scheme `island` registered for `widgetURL`.

**Correctness**
- `Activity.request` only from `IslandActivityManager.start` (MainActor).
- End stale activities on launch and on `start` (crash leftovers).
- `staleDate` on every update (5 min) so a dead app's card does not lie.
- `dismissalPolicy: .immediate` when ending, `.after` for a short "Call ended" state.
- Payload < 4 KB: truncate titles to 24 chars, no images/URLs in state.
- Intents must be idempotent: mute toggles read current state, never a local flag.

**QA**
- [ ] Tap compact → call screen; tap expanded non-button → call screen.
- [ ] Long-press → expanded card with controls; no app launch.
- [ ] Mute from island updates the app UI and vice versa.
- [ ] Timer keeps counting after 10 min in background (system timer).
- [ ] Two tasks: switcher rotates focus; compact shows first name only.
- [ ] End call from island → card collapses, app call ends.
- [ ] Reduce Motion, RTL, CJK names, Dynamic Type up to XL.
- [ ] Activity survives app force-quit (state in activity, not memory).

---

## 9. Integration steps

1. Add the widget extension target (`IslandWidget`) to the app.
2. Put `IslandAttributes.swift` in **both** targets (attributes must be shared).
3. Add `IslandLiveActivity.swift`, `IslandIntents.swift` to the extension.
4. Add `IslandActivityManager.swift`, `LevelMeter.swift`, `AppShell.swift`,
   `IslandReplica.swift` to the app target.
5. Add `NSSupportsLiveActivities`, the URL scheme and microphone usage string.
6. Wire the call lifecycle: `start` on connect, `pushLevels` from `LevelMeter`,
   `toggleMute/end` through CallKit so system and island stay in sync.
