import ActivityKit
import Foundation

/// Attributes are the *stable* part of the call: everything that does not change
/// while the call runs. Anything that ticks belongs in `ContentState`.
public struct CallActivityAttributes: ActivityAttributes {

    public struct ContentState: Codable, Hashable, Sendable {
        public var isMuted: Bool
        public var route: AudioRoute
        public var phase: CallPhase
        /// Five normalised mic levels (0…1), not a single scalar: one number
        /// cannot preserve the shape of the bars between updates.
        public var levels: [Double]
        public var updatedAt: Date

        public init(
            isMuted: Bool = false,
            route: AudioRoute = .receiver,
            phase: CallPhase = .connecting,
            levels: [Double] = [0.2, 0.4, 0.3, 0.45, 0.25],
            updatedAt: Date = .now
        ) {
            self.isMuted = isMuted
            self.route = route
            self.phase = phase
            self.levels = levels
            self.updatedAt = updatedAt
        }
    }

    public var callId: String
    public var contactName: String
    public var callStartTime: Date
    public var avatarURL: URL?

    public init(
        callId: String,
        contactName: String,
        callStartTime: Date = .now,
        avatarURL: URL? = nil
    ) {
        self.callId = callId
        self.contactName = contactName
        self.callStartTime = callStartTime
        self.avatarURL = avatarURL
    }
}

public enum AudioRoute: String, Codable, Hashable, Sendable {
    case receiver, speaker, bluetooth, headphones

    public var label: String {
        switch self {
        case .receiver: "iPhone"
        case .speaker: "Speaker"
        case .bluetooth: "Bluetooth"
        case .headphones: "Headphones"
        }
    }

    public var next: AudioRoute {
        switch self {
        case .receiver: .speaker
        case .speaker: .bluetooth
        case .bluetooth: .headphones
        case .headphones: .receiver
        }
    }
}

public enum CallPhase: String, Codable, Hashable, Sendable {
    case connecting, active, ended
}

// MARK: - Display helpers

public extension CallActivityAttributes {
    /// Compact leading has ~50 pt: first name only, truncation-safe.
    var compactName: String {
        contactName.split(separator: " ").first.map(String.init) ?? contactName
    }

    var initial: String {
        contactName.first.map { String($0).uppercased() } ?? "?"
    }
}
