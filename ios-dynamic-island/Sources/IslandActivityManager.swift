import ActivityKit
import Foundation

public enum IslandActivityError: Error {
    case activitiesDisabled
}

/// Single owner of the Live Activity. One activity hosts every concurrent task,
/// because the island has exactly one slot: adding a second `Activity` makes the
/// system pick whichever updated last, which is how calls lose their island.
@MainActor
public final class IslandActivityManager {

    public static let shared = IslandActivityManager()

    private var activity: Activity<IslandAttributes>?
    private var state: IslandAttributes.ContentState?
    private var lastLevelPush = Date.distantPast

    private init() {}

    public var isRunning: Bool { activity != nil }
    public var currentState: IslandAttributes.ContentState? { state }
    public var activityID: String? { activity?.id }

    // MARK: Lifecycle

    public func start(with tasks: [IslandTask]) async throws {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            throw IslandActivityError.activitiesDisabled
        }
        endStaleActivities()

        let initial = IslandAttributes.ContentState(
            tasks: tasks,
            activeTaskID: tasks.first?.id
        )
        let content = ActivityContent(
            state: initial,
            staleDate: .now.addingTimeInterval(60 * 60)
        )
        activity = try Activity.request(
            attributes: IslandAttributes(),
            content: content,
            pushType: nil
        )
        state = initial
    }

    public func endAll() async {
        await activity?.end(nil, dismissalPolicy: .immediate)
        activity = nil
        state = nil
    }

    /// Ends activities left behind by a previous process (crash, force quit).
    private func endStaleActivities() {
        for stale in Activity<IslandAttributes>.activities {
            Task { await stale.end(nil, dismissalPolicy: .immediate) }
        }
    }

    // MARK: Mutation

    public func update(_ mutate: (inout IslandAttributes.ContentState) -> Void) async {
        guard let activity, var next = state else { return }
        mutate(&next)
        next.updatedAt = .now
        state = next

        let content = ActivityContent(
            state: next,
            staleDate: .now.addingTimeInterval(5 * 60)
        )
        await activity.update(content)
    }

    /// Waveform levels, throttled to ~6 Hz. Dropped entirely while muted so the
    /// island stops consuming its update budget.
    public func pushLevels(_ levels: [Double]) async {
        let now = Date()
        guard now.timeIntervalSince(lastLevelPush) > 0.16 else { return }
        guard let task = state?.activeTask, task.kind == .call, !task.isMuted else { return }
        lastLevelPush = now
        await update { $0.levels = levels }
    }

    public func focus(taskID: String) async {
        await update { $0.activeTaskID = taskID }
    }

    public func focusNext() async {
        guard let state, state.tasks.count > 1 else { return }
        let index = state.tasks.firstIndex { $0.id == state.activeTaskID } ?? 0
        let next = state.tasks[(index + 1) % state.tasks.count]
        await update { $0.activeTaskID = next.id }
    }

    public func focusPrevious() async {
        guard let state, state.tasks.count > 1 else { return }
        let index = state.tasks.firstIndex { $0.id == state.activeTaskID } ?? 0
        let previous = state.tasks[(index - 1 + state.tasks.count) % state.tasks.count]
        await update { $0.activeTaskID = previous.id }
    }

    public func toggleMute(taskID: String) async {
        await update { state in
            guard let index = state.tasks.firstIndex(where: { $0.id == taskID }) else { return }
            state.tasks[index].isMuted.toggle()
        }
    }

    public func cycleRoute(taskID: String) async {
        await update { state in
            guard let index = state.tasks.firstIndex(where: { $0.id == taskID }) else { return }
            state.tasks[index].route = state.tasks[index].route.next
        }
    }

    public func togglePause(taskID: String) async {
        await update { state in
            guard let index = state.tasks.firstIndex(where: { $0.id == taskID }) else { return }
            let task = state.tasks[index]
            if task.phase == .paused {
                // resume: shift the anchor so the system timer keeps counting
                let elapsed = Date.now.timeIntervalSince(task.startedAt)
                state.tasks[index].startedAt = .now.addingTimeInterval(-elapsed)
                state.tasks[index].phase = .active
            } else {
                state.tasks[index].phase = .paused
            }
        }
    }

    public func end(taskID: String) async {
        guard var current = state else { return }
        current.tasks.removeAll { $0.id == taskID }
        if current.tasks.isEmpty {
            await endAll()
            return
        }
        if current.activeTaskID == taskID {
            current.activeTaskID = current.tasks.first?.id
        }
        current.updatedAt = .now
        state = current
        await activity?.update(ActivityContent(state: current, staleDate: .now.addingTimeInterval(300)))
    }
}
