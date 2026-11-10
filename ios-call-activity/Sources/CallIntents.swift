import AppIntents
import Foundation

struct ToggleMuteIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Toggle microphone"

    @Parameter(title: "Call") var callId: String

    init() {}
    init(callId: String) { self.callId = callId }

    @MainActor
    func perform() async throws -> some IntentResult {
        await CallActivityController.shared.toggleMute()
        return .result()
    }
}

struct CycleRouteIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Change audio route"

    @Parameter(title: "Call") var callId: String

    init() {}
    init(callId: String) { self.callId = callId }

    @MainActor
    func perform() async throws -> some IntentResult {
        await CallActivityController.shared.cycleRoute()
        return .result()
    }
}

struct EndCallIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "End call"

    @Parameter(title: "Call") var callId: String

    init() {}
    init(callId: String) { self.callId = callId }

    @MainActor
    func perform() async throws -> some IntentResult {
        await CallActivityController.shared.end()
        return .result()
    }
}
