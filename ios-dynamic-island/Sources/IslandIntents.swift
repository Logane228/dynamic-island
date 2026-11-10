import AppIntents
import Foundation

// Intents shown inside the island run in the app's process without bringing it
// to the foreground. That is what makes "control without opening the app" work.

struct ToggleMuteIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Toggle microphone"

    @Parameter(title: "Task") var taskID: String

    init() {}
    init(taskID: String) { self.taskID = taskID }

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.toggleMute(taskID: taskID)
        return .result()
    }
}

struct CycleRouteIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Change audio route"

    @Parameter(title: "Task") var taskID: String

    init() {}
    init(taskID: String) { self.taskID = taskID }

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.cycleRoute(taskID: taskID)
        return .result()
    }
}

struct TogglePauseIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Pause or resume"

    @Parameter(title: "Task") var taskID: String

    init() {}
    init(taskID: String) { self.taskID = taskID }

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.togglePause(taskID: taskID)
        return .result()
    }
}

struct TogglePlaybackIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Play or pause"

    @Parameter(title: "Task") var taskID: String

    init() {}
    init(taskID: String) { self.taskID = taskID }

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.togglePause(taskID: taskID)
        return .result()
    }
}

struct EndTaskIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "End activity"

    @Parameter(title: "Task") var taskID: String

    init() {}
    init(taskID: String) { self.taskID = taskID }

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.end(taskID: taskID)
        return .result()
    }
}

/// The sanctioned replacement for a horizontal swipe: a button that rotates the
/// visible task inside the same activity. Widgets cannot receive drag gestures,
/// so the "swipe between activities" affordance has to be a tappable one.
struct FocusNextTaskIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Next activity"

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.focusNext()
        return .result()
    }
}

struct FocusPreviousTaskIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Previous activity"

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await IslandActivityManager.shared.focusPrevious()
        return .result()
    }
}
