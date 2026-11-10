import ActivityKit
import Foundation

public enum CallActivityError: Error {
    case activitiesDisabled
}

/// Owns the single call activity. Everything the island can change flows through
/// here, so the widget never becomes a second source of truth.
@MainActor
public final class CallActivityController {

    public static let shared = CallActivityController()

    private var activity: Activity<CallActivityAttributes>?
    private var state: CallActivityAttributes.ContentState?
    private var lastLevelPush = Date.distantPast

    private init() {}

    public var isRunning: Bool { activity != nil }

    // MARK: Lifecycle

    public func start(callId: String, contactName: String, startedAt: Date = .now) async throws {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            throw CallActivityError.activitiesDisabled
        }
        endStaleActivities()

        let initial = CallActivityAttributes.ContentState(phase: .connecting)
        activity = try Activity.request(
            attributes: CallActivityAttributes(
                callId: callId,
                contactName: contactName,
                callStartTime: startedAt
            ),
            content: ActivityContent(state: initial, staleDate: nil),
            pushType: nil
        )
        state = initial
    }

    public func markConnected() async {
        await update { $0.phase = .active }
    }

    public func end() async {
        await update { $0.phase = .ended }
        try? await Task.sleep(for: .seconds(2))
        await activity?.end(nil, dismissalPolicy: .immediate)
        activity = nil
        state = nil
    }

    // MARK: Live data

    /// ~6 Hz, dropped while muted, so the island never burns its update budget.
    public func pushLevels(_ levels: [Double]) async {
        let now = Date()
        guard now.timeIntervalSince(lastLevelPush) > 0.16 else { return }
        guard let current = state, current.phase == .active, !current.isMuted else { return }
        lastLevelPush = now
        await update { $0.levels = levels }
    }

    // MARK: Controls

    public func toggleMute() async {
        await update { $0.isMuted.toggle() }
    }

    public func cycleRoute() async {
        await update { $0.route = $0.route.next }
    }

    // MARK: Plumbing

    private func update(_ mutate: (inout CallActivityAttributes.ContentState) -> Void) async {
        guard let activity, var next = state else { return }
        mutate(&next)
        next.updatedAt = .now
        state = next
        await activity.update(
            ActivityContent(state: next, staleDate: .now.addingTimeInterval(60))
        )
    }

    private func endStaleActivities() {
        for stale in Activity<CallActivityAttributes>.activities {
            Task { await stale.end(nil, dismissalPolicy: .immediate) }
        }
    }
}
