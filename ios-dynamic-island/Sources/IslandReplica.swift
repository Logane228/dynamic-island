import SwiftUI

/// In-app replica of the island.
///
/// Important: the real Dynamic Island cannot host custom gesture recognizers.
/// Tap is delivered as `widgetURL`, controls are `Button(intent:)`, and
/// expansion is a system long-press. This replica is the component you use for
/// the call screen, for design QA and for prototyping the exact gesture model
/// (tap / long-press / horizontal swipe) with the same spring feel.
struct IslandReplica: View {
    enum Mode { case compact, expanded }

    @State private var mode: Mode = .compact
    @State private var dragX: CGFloat = 0
    @State private var muted = false
    @State private var routeIndex = 0
    @State private var taskIndex = 0
    @State private var started = Date.now

    private let routes = ["Phone", "Speaker", "Bluetooth"]
    private let tasks = ["Camille Laurent", "Focus timer"]

    private var expanded: Bool { mode == .expanded }

    var body: some View {
        VStack {
            island
                .padding(.top, 22)
            Spacer(minLength: 0)
            Text("tap · long-press · swipe ← →")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.35))
                .padding(.bottom, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: Island shell

    private var island: some View {
        content
            .frame(width: expanded ? 340 : 232, height: expanded ? 188 : 38)
            .background(Color.black)
            .clipShape(RoundedRectangle(cornerRadius: expanded ? 42 : 19, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: expanded ? 42 : 19, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.09), lineWidth: 0.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: expanded ? 42 : 19, style: .continuous))
            .offset(x: dragX * 0.4)
            .onTapGesture { toggle() }
            .onLongPressGesture(minimumDuration: 0.35) {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.78)) { mode = .expanded }
            }
            .gesture(swipe)
            .animation(.spring(response: 0.42, dampingFraction: 0.78), value: mode)
            .animation(.interactiveSpring(response: 0.3, dampingFraction: 0.82), value: dragX)
    }

    @ViewBuilder
    private var content: some View {
        if expanded { expandedContent } else { compactContent }
    }

    // MARK: Compact

    private var compactContent: some View {
        HStack(spacing: 8) {
            LiveWaveform(muted: muted)
                .frame(width: 24, height: 16)

            Text(shortName)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)

            Spacer(minLength: 0)

            Text(timerInterval: started...Date.distantFuture, countsDown: false)
                .font(.system(size: 12.5, weight: .medium).monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
    }

    // MARK: Expanded

    private var expandedContent: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Circle()
                    .fill(LinearGradient(colors: [.blue, .purple], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 38, height: 38)
                    .overlay(Text("C").font(.system(size: 15, weight: .semibold)))

                VStack(alignment: .leading, spacing: 2) {
                    Text(tasks[taskIndex])
                        .font(.system(size: 15.5, weight: .semibold))
                        .lineLimit(1)
                    HStack(spacing: 4) {
                        Text(routes[routeIndex])
                        Text("·")
                        Text(timerInterval: started...Date.distantFuture, countsDown: false)
                            .monospacedDigit()
                    }
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.3))
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                    taskIndex = (taskIndex + 1) % tasks.count
                }
            }

            Spacer(minLength: 6)

            HStack(spacing: 22) {
                control(symbol: muted ? "mic.slash.fill" : "mic.fill",
                        title: muted ? "Unmute" : "Mute", active: muted) {
                    muted.toggle()
                }
                control(symbol: "speaker.wave.2.fill", title: routes[routeIndex]) {
                    routeIndex = (routeIndex + 1) % routes.count
                }
                control(symbol: "phone.down.fill", title: "End", tint: .red) {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) { mode = .compact }
                }
            }
            .padding(.bottom, 16)
        }
    }

    // MARK: Gestures

    private var swipe: some Gesture {
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
    }

    private func toggle() {
        withAnimation(.spring(response: 0.42, dampingFraction: 0.78)) {
            mode = expanded ? .compact : .expanded
        }
    }

    // MARK: Bits

    private var shortName: String {
        tasks[taskIndex].split(separator: " ").first.map(String.init) ?? tasks[taskIndex]
    }

    private func control(symbol: String, title: String, active: Bool = false, tint: Color = .white, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .frame(width: 42, height: 42)
                    .background(active ? Color.white : Color.white.opacity(0.14), in: Circle())
                    .foregroundStyle(active ? Color.black : tint)
                Text(title)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
    }
}

/// Only used inside the app — the widget version takes static levels.
struct LiveWaveform: View {
    let muted: Bool

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 12.0)) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            HStack(spacing: 2) {
                ForEach(0..<5, id: \.self) { index in
                    let height = muted ? 2.0 : 3.0 + 11.0 * abs(sin(time * 2.2 + Double(index) * 0.7))
                    Capsule()
                        .fill(muted ? Color.gray : Color.green)
                        .frame(width: 2.5, height: height)
                }
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: muted)
        .accessibilityHidden(true)
    }
}
