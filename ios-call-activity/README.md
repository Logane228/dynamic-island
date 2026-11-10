# CallActivityAttributes — review + production drop-in

Your model is a good start; five things block it from shipping. Each fix is in
`Sources/` as ready-to-paste code.

## What I changed and why

| Your code | Problem | Fix |
| --- | --- | --- |
| `contactName` in `ContentState` | The name never changes, but every level update rewrites it — wasted payload and a chance for the two to drift | moved to **attributes** (`contactName`) |
| `callStartTime` in `ContentState` | Same: constant per call; and updates must not move it | moved to **attributes** (`callStartTime`) |
| `audioLevel: Double` | One scalar flattens the bars; every update redraws five identical heights | `levels: [Double]` — five buckets |
| no `route` / `phase` | Cannot show “Speaker · Live”, “Connecting…”, “Call ended”; no way to freeze the timer | added `AudioRoute` and `CallPhase` |
| no public inits, no `Sendable` | Widget extension + app in separate modules cannot construct it; Swift 6 concurrency warnings | `public init`s, `Sendable` on state and enums |
| `callId: String` only | Fine, but add `avatarURL` now to avoid a second migration | `avatarURL: URL?` |

Also: `ContentState` must stay small — five levels + three enums is ~200 bytes,
well under the ~4 KB budget.

## The model

```swift
public struct CallActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable, Sendable {
        public var isMuted: Bool
        public var route: AudioRoute
        public var phase: CallPhase
        public var levels: [Double]   // 5 buckets, 0…1
        public var updatedAt: Date
    }
    public var callId: String
    public var contactName: String
    public var callStartTime: Date
    public var avatarURL: URL?
}
```

## What’s included

| File | Contents |
| --- | --- |
| `CallActivityAttributes.swift` | model + `AudioRoute`/`CallPhase` + compact-name helpers |
| `CallLiveActivity.swift` | `ActivityConfiguration`, all four regions, lock screen, waveform, controls |
| `CallIntents.swift` | `ToggleMuteIntent`, `CycleRouteIntent`, `EndCallIntent` (`LiveActivityIntent`) |
| `CallActivityController.swift` | one owner: start / connect / pushLevels (6 Hz) / mute / route / end |

## Wiring

```swift
// call connected
try await CallActivityController.shared.start(callId: call.id, contactName: call.displayName)
await CallActivityController.shared.markConnected()

// mic metering (LevelMeter from the other spec)
meter.onLevels = { levels in
    Task { @MainActor in await CallActivityController.shared.pushLevels(levels) }
}

// hang up
await CallActivityController.shared.end()
```

`widgetURL("callapp://call/<id>")` is the single tap route; handle it with
`onOpenURL` and push the call screen.

## Two things to know

1. **Only one island slot.** This model is per-call. If you need a call *and* a
   timer, use the multi-task `IslandAttributes` version (`ios-dynamic-island/`) —
   it hosts several tasks in one activity and adds the switcher.
2. **No swipe, no long-press handler.** Tapping the island opens the app; long
   press expands (system); controls are `Button(intent:)`. Any swipe must live in
   the app’s call screen, not in the widget.
