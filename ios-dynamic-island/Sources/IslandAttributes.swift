import Foundation
import ActivityKit

// MARK: - Domain

public enum IslandTaskKind: String, Codable, Hashable, Sendable {
    case call, timer, media, delivery
}

public enum IslandPhase: String, Codable, Hashable, Sendable {
    case connecting, active, paused, ended
}

public enum AudioRoute: String, Codable, Hashable, Sendable {
    case receiver, speaker, bluetooth, headphones

    public var label: String {
        switch self {
        case .receiver: "Phone"
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

/// One concurrent activity. Kept deliberately small: five tasks with short
/// strings stay far below the ~4 KB ActivityKit payload budget.
public struct IslandTask: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var kind: IslandTaskKind
    public var title: String
    public var subtitle: String?
    public var startedAt: Date
    public var endsAt: Date?
    public var progress: Double?
    public var isMuted: Bool
    public var route: AudioRoute
    public var phase: IslandPhase

    public init(
        id: String,
        kind: IslandTaskKind,
        title: String,
        subtitle: String? = nil,
        startedAt: Date = .now,
        endsAt: Date? = nil,
        progress: Double? = nil,
        isMuted: Bool = false,
        route: AudioRoute = .receiver,
        phase: IslandPhase = .active
    ) {
        self.id = id
        self.kind = kind
        self.title = title
        self.subtitle = subtitle
        self.startedAt = startedAt
        self.endsAt = endsAt
        self.progress = progress
        self.isMuted = isMuted
        self.route = route
        self.phase = phase
    }
}

// MARK: - Attributes

public struct IslandAttributes: ActivityAttributes {

    public struct ContentState: Codable, Hashable, Sendable {
        /// Ordered, most relevant first.
        public var tasks: [IslandTask]
        /// Which task the island renders right now. One island slot only.
        public var activeTaskID: String?
        /// Five normalised mic levels for the waveform (0…1).
        public var levels: [Double]
        public var updatedAt: Date

        public init(
            tasks: [IslandTask],
            activeTaskID: String? = nil,
            levels: [Double] = [0.2, 0.4, 0.32, 0.46, 0.26],
            updatedAt: Date = .now
        ) {
            self.tasks = tasks
            self.activeTaskID = activeTaskID ?? tasks.first?.id
            self.levels = levels
            self.updatedAt = updatedAt
        }
    }

    public var sessionID: UUID
    public var appName: String

    public init(sessionID: UUID = UUID(), appName: String = "Island") {
        self.sessionID = sessionID
        self.appName = appName
    }
}

// MARK: - Read helpers used by the widget

public extension IslandAttributes.ContentState {
    var activeTask: IslandTask? {
        if let activeTaskID, let match = tasks.first(where: { $0.id == activeTaskID }) {
            return match
        }
        return tasks.first
    }

    func task(id: String) -> IslandTask? {
        tasks.first { $0.id == id }
    }

    var hasMultipleTasks: Bool { tasks.count > 1 }

    /// Compact leading text: first name only, hard-capped so the sensor gap
    /// never eats the layout.
    var compactTitle: String {
        guard let task = activeTask else { return "" }
        if task.kind == .call {
            return task.title.split(separator: " ").first.map(String.init) ?? task.title
        }
        return task.title
    }

    /// Compact trailing value: running clock for call/timer, percent for transfers.
    var compactTrailingText: String? {
        guard let task = activeTask else { return nil }
        if task.kind == .delivery, let progress = task.progress {
            return "\(Int(progress * 100))%"
        }
        return nil
    }

    var shortestTaskTitle: String {
        tasks.first(where: { $0.id == activeTaskID })?.title ?? tasks.first?.title ?? ""
    }
}
