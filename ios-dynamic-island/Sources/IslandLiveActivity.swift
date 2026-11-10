import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Widget

struct IslandLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: IslandAttributes.self) { context in
            LockScreenIslandView(context: context)
                .activityBackgroundTint(.black)
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            let state = context.state

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    TaskAvatar(task: state.activeTask)
                        .padding(.leading, 2)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    TaskClock(task: state.activeTask)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .frame(minWidth: 44, alignment: .trailing)
                        .padding(.trailing, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(state.activeTask?.title ?? "")
                            .font(.system(size: 15, weight: .semibold))
                            .lineLimit(1)
                        Text(subtitle(for: state.activeTask))
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ExpandedControls(state: state)
                }
            } compactLeading: {
                CompactLeading(state: state)
            } compactTrailing: {
                CompactTrailing(state: state)
            } minimal: {
                MinimalView(task: state.activeTask)
            }
            .keylineTint(state.activeTask?.kind == .call ? .green : .orange)
            .widgetURL(deepLink(for: state))
        }
    }

    private func subtitle(for task: IslandTask?) -> String {
        guard let task else { return "" }
        switch task.kind {
        case .call:
            return "\(task.route.label) · Live"
        case .timer:
            return task.phase == .paused ? "Paused" : "Timer"
        case .media:
            return task.subtitle ?? "Now playing"
        case .delivery:
            return task.progress.map { "\(Int($0 * 100))% complete" } ?? "In progress"
        }
    }

    private func deepLink(for state: IslandAttributes.ContentState) -> URL? {
        var components = URLComponents()
        components.scheme = "island"
        components.host = "open"
        if let id = state.activeTask?.id {
            components.queryItems = [URLQueryItem(name: "task", value: id)]
        }
        return components.url
    }
}

// MARK: - Compact

struct CompactLeading: View {
    let state: IslandAttributes.ContentState

    var body: some View {
        HStack(spacing: 6) {
            if state.hasMultipleTasks {
                HStack(spacing: 3) {
                    ForEach(state.tasks.prefix(3)) { task in
                        Circle()
                            .fill(task.id == state.activeTaskID ? Color.white : Color.white.opacity(0.28))
                            .frame(width: 4, height: 4)
                    }
                }
            }

            if state.activeTask?.kind == .call {
                Waveform(levels: state.levels, muted: state.activeTask?.isMuted ?? false)
                    .frame(width: 18, height: 14)
            } else if let task = state.activeTask {
                Image(systemName: glyph(for: task))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(task.kind == .delivery ? .orange : .green)
            }

            Text(state.compactTitle)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    private func glyph(for task: IslandTask) -> String {
        switch task.kind {
        case .call: "phone.fill"
        case .timer: "timer"
        case .media: "play.fill"
        case .delivery: "shippingbox.fill"
        }
    }
}

struct CompactTrailing: View {
    let state: IslandAttributes.ContentState

    var body: some View {
        TaskClock(task: state.activeTask)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(.secondary)
            .frame(width: 44, alignment: .trailing)
            .lineLimit(1)
    }
}

struct MinimalView: View {
    let task: IslandTask?

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(color)
    }

    private var symbol: String {
        guard let task else { return "circle" }
        switch task.kind {
        case .call: return task.isMuted ? "mic.slash.fill" : "phone.fill"
        case .timer: return "timer"
        case .media: return "play.fill"
        case .delivery: return "shippingbox.fill"
        }
    }

    private var color: Color {
        guard let task else { return .white }
        return task.kind == .delivery ? .orange : .green
    }
}

// MARK: - Primitives

/// State-driven bars: Live Activity views are snapshots, so the shape comes from
/// five levels pushed by the app at ~6 Hz, animated between snapshots.
struct Waveform: View {
    let levels: [Double]
    let muted: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 2) {
            ForEach(0..<min(levels.count, 5), id: \.self) { index in
                Capsule(style: .continuous)
                    .fill(muted ? Color.gray.opacity(0.35) : Color.green)
                    .frame(width: 2, height: muted ? 2 : max(3, CGFloat(levels[index]) * 14))
            }
        }
        .frame(maxHeight: 14, alignment: .center)
        .animation(.spring(response: 0.22, dampingFraction: 0.65), value: levels)
        .animation(.spring(response: 0.26, dampingFraction: 0.7), value: muted)
        .accessibilityHidden(true)
    }
}

struct TaskClock: View {
    let task: IslandTask?

    @ViewBuilder
    var body: some View {
        if let task {
            switch task.kind {
            case .call, .timer:
                if task.phase == .paused {
                    Text(Self.elapsed(from: task.startedAt))
                        .monospacedDigit()
                } else {
                    Text(timerInterval: task.startedAt...Date.distantFuture, countsDown: false)
                        .monospacedDigit()
                }
            case .delivery, .media:
                if let progress = task.progress {
                    Text("\(Int(progress * 100))%")
                } else {
                    Text("")
                }
            }
        } else {
            Text("")
        }
    }

    static func elapsed(from start: Date) -> String {
        let total = max(0, Int(Date.now.timeIntervalSince(start)))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

struct TaskAvatar: View {
    let task: IslandTask?

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color(red: 0.31, green: 0.55, blue: 1.0),
                                 Color(red: 0.48, green: 0.30, blue: 1.0)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Text(initial)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(width: 38, height: 38)
    }

    private var initial: String {
        guard let first = task?.title.first else { return "?" }
        return String(first).uppercased()
    }
}

// MARK: - Expanded controls

struct ExpandedControls: View {
    let state: IslandAttributes.ContentState

    var body: some View {
        VStack(spacing: 6) {
            if state.hasMultipleTasks {
                switcher
            }
            controls
        }
        .padding(.top, 2)
    }

    private var switcher: some View {
        HStack(spacing: 10) {
            Button(intent: FocusPreviousTaskIntent()) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 11, weight: .bold))
                    .frame(width: 28, height: 22)
            }
            .buttonStyle(.plain)

            HStack(spacing: 4) {
                ForEach(state.tasks) { task in
                    Capsule()
                        .fill(task.id == state.activeTaskID ? Color.white : Color.white.opacity(0.25))
                        .frame(width: task.id == state.activeTaskID ? 14 : 6, height: 5)
                        .animation(.spring(response: 0.28, dampingFraction: 0.8), value: state.activeTaskID)
                }
            }

            Button(intent: FocusNextTaskIntent()) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .bold))
                    .frame(width: 28, height: 22)
            }
            .buttonStyle(.plain)
        }
        .foregroundStyle(.secondary)
    }

    @ViewBuilder
    private var controls: some View {
        if let task = state.activeTask {
            switch task.kind {
            case .call:
                HStack(spacing: 22) {
                    Button(intent: ToggleMuteIntent(taskID: task.id)) {
                        ControlGlyph(
                            systemName: task.isMuted ? "mic.slash.fill" : "mic.fill",
                            title: task.isMuted ? "Unmute" : "Mute",
                            active: task.isMuted
                        )
                    }
                    Button(intent: CycleRouteIntent(taskID: task.id)) {
                        ControlGlyph(systemName: "speaker.wave.2.fill", title: task.route.label)
                    }
                    Button(intent: EndTaskIntent(taskID: task.id)) {
                        ControlGlyph(systemName: "phone.down.fill", title: "End", tint: .red)
                    }
                }
                .buttonStyle(.plain)

            case .timer:
                HStack(spacing: 22) {
                    Button(intent: TogglePauseIntent(taskID: task.id)) {
                        ControlGlyph(
                            systemName: task.phase == .paused ? "play.fill" : "pause.fill",
                            title: task.phase == .paused ? "Resume" : "Pause"
                        )
                    }
                    Button(intent: EndTaskIntent(taskID: task.id)) {
                        ControlGlyph(systemName: "xmark", title: "Stop", tint: .red)
                    }
                }
                .buttonStyle(.plain)

            case .media:
                HStack(spacing: 22) {
                    Button(intent: TogglePlaybackIntent(taskID: task.id)) {
                        ControlGlyph(
                            systemName: task.phase == .paused ? "play.fill" : "pause.fill",
                            title: task.phase == .paused ? "Play" : "Pause"
                        )
                    }
                    Button(intent: EndTaskIntent(taskID: task.id)) {
                        ControlGlyph(systemName: "xmark", title: "Close")
                    }
                }
                .buttonStyle(.plain)

            case .delivery:
                HStack(spacing: 22) {
                    Button(intent: EndTaskIntent(taskID: task.id)) {
                        ControlGlyph(systemName: "xmark", title: "Dismiss")
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct ControlGlyph: View {
    let systemName: String
    let title: String
    var active: Bool = false
    var tint: Color = .white

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .frame(width: 40, height: 40)
                .background(
                    active ? Color.white : Color.white.opacity(0.14),
                    in: Circle()
                )
                .foregroundStyle(active ? Color.black : tint)
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

// MARK: - Lock screen

struct LockScreenIslandView: View {
    let context: ActivityViewContext<IslandAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                TaskAvatar(task: context.state.activeTask)
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.activeTask?.title ?? "")
                        .font(.system(size: 16, weight: .semibold))
                    Text(context.state.activeTask?.subtitle ?? "")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                TaskClock(task: context.state.activeTask)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            ExpandedControls(state: context.state)
                .frame(maxWidth: .infinity)
        }
        .padding(14)
        .activitySystemActionForegroundColor(.white)
    }
}
