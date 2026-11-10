import ActivityKit
import SwiftUI
import WidgetKit

struct CallLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CallActivityAttributes.self) { context in
            LockScreenCallView(context: context)
                .activityBackgroundTint(.black)
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            let state = context.state

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    CallAvatar(initial: context.attributes.initial)
                        .padding(.leading, 2)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    CallClock(start: context.attributes.callStartTime)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .frame(minWidth: 44, alignment: .trailing)
                        .padding(.trailing, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.attributes.contactName)
                            .font(.system(size: 15, weight: .semibold))
                            .lineLimit(1)
                        Text(subtitle(for: state))
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    CallControls(state: state, callId: context.attributes.callId)
                }
            } compactLeading: {
                HStack(spacing: 5) {
                    Waveform(levels: state.levels, muted: state.isMuted)
                        .frame(width: 18, height: 14)
                    Text(context.attributes.compactName)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            } compactTrailing: {
                CallClock(start: context.attributes.callStartTime)
                    .font(.system(size: 13, weight: .medium))
                    .frame(width: 44, alignment: .trailing)
            } minimal: {
                Image(systemName: state.isMuted ? "mic.slash.fill" : "phone.fill")
                    .foregroundStyle(.green)
            }
            .keylineTint(.green)
            .widgetURL(URL(string: "callapp://call/\(context.attributes.callId)"))
        }
    }

    private func subtitle(for state: CallActivityAttributes.ContentState) -> String {
        switch state.phase {
        case .connecting: "Connecting…"
        case .ended: "Call ended"
        case .active: "\(state.route.label) · Live"
        }
    }
}

// MARK: - Pieces

struct CallClock: View {
    let start: Date

    var body: some View {
        Text(timerInterval: start...Date.distantFuture, countsDown: false)
            .monospacedDigit()
            .lineLimit(1)
    }
}

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

struct CallAvatar: View {
    let initial: String

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
}

struct CallControls: View {
    let state: CallActivityAttributes.ContentState
    let callId: String

    var body: some View {
        HStack(spacing: 24) {
            Button(intent: ToggleMuteIntent(callId: callId)) {
                ControlGlyph(
                    systemName: state.isMuted ? "mic.slash.fill" : "mic.fill",
                    title: state.isMuted ? "Unmute" : "Mute",
                    active: state.isMuted
                )
            }
            Button(intent: CycleRouteIntent(callId: callId)) {
                ControlGlyph(systemName: "speaker.wave.2.fill", title: state.route.label)
            }
            Button(intent: EndCallIntent(callId: callId)) {
                ControlGlyph(systemName: "phone.down.fill", title: "End", tint: .red)
            }
        }
        .buttonStyle(.plain)
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
                .background(active ? Color.white : Color.white.opacity(0.14), in: Circle())
                .foregroundStyle(active ? Color.black : tint)
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

// MARK: - Lock screen

struct LockScreenCallView: View {
    let context: ActivityViewContext<CallActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                CallAvatar(initial: context.attributes.initial)
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.contactName)
                        .font(.system(size: 16, weight: .semibold))
                    Text(context.state.phase == .active ? context.state.route.label : "Call")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                CallClock(start: context.attributes.callStartTime)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            CallControls(state: context.state, callId: context.attributes.callId)
                .frame(maxWidth: .infinity)
        }
        .padding(16)
    }
}
