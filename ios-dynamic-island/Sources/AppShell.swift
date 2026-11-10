import SwiftUI
import Observation

// MARK: - Routing

/// One destination for the island: a single tap anywhere that is not a button
/// resolves here through `widgetURL` + `onOpenURL`.
@MainActor
@Observable
final class AppRouter {
    enum Route: Hashable {
        case call(taskID: String?)
    }

    var path: [Route] = []

    func handle(_ url: URL) {
        guard url.scheme == "island" else { return }
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "task" })?.value
        switch url.host {
        case "open":
            path = [.call(taskID: query)]
        default:
            break
        }
    }
}

// MARK: - App

@main
struct IslandApp: App {
    @State private var router = AppRouter()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(router)
                .onOpenURL { router.handle($0) }
        }
    }
}

struct RootView: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        @Bindable var router = router
        NavigationStack(path: $router.path) {
            HomeView()
                .navigationDestination(for: AppRouter.Route.self) { route in
                    switch route {
                    case .call(let taskID):
                        CallScreen(taskID: taskID)
                    }
                }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Home

struct HomeView: View {
    @Environment(AppRouter.self) private var router
    @State private var running = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header
                IslandReplica()
                    .frame(height: 300)
                    .background(Color.black, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .strokeBorder(.white.opacity(0.08), lineWidth: 0.5)
                    )

                Text("The replica above implements tap / long-press / horizontal swipe locally. The real Dynamic Island cannot host those gestures — it uses widgetURL and App Intents, which is what this demo wires up.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                actions
            }
            .padding(20)
        }
        .background(Color(red: 0.03, green: 0.035, blue: 0.06).ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Island · Live Activity")
                .font(.system(size: 28, weight: .bold))
            Text("One activity, many concurrent tasks")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var actions: some View {
        VStack(spacing: 10) {
            Button {
                Task {
                    try? await IslandActivityManager.shared.start(with: [
                        IslandTask(id: "call", kind: .call, title: "Camille Laurent", subtitle: "VoIP call"),
                        IslandTask(id: "timer", kind: .timer, title: "Focus timer",
                                   startedAt: .now.addingTimeInterval(-768))
                    ])
                    running = true
                }
            } label: {
                Label(running ? "Activity running" : "Start call + timer activity", systemImage: "phone.fill")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.green.opacity(0.18), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .disabled(running)

            Button {
                Task {
                    await IslandActivityManager.shared.endAll()
                    running = false
                }
            } label: {
                Label("End activity", systemImage: "xmark")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }

            Button {
                router.path = [.call(taskID: "call")]
            } label: {
                Label("Open call screen (single-tap route)", systemImage: "arrow.up.right")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.blue.opacity(0.18), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        .foregroundStyle(.white)
    }
}

// MARK: - Call screen

struct CallScreen: View {
    let taskID: String?
    @State private var muted = false
    @State private var route: AudioRoute = .receiver
    @State private var started = Date.now

    var body: some View {
        VStack(spacing: 18) {
            Circle()
                .fill(LinearGradient(colors: [.blue, .purple], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 104, height: 104)
                .overlay(Text("C").font(.system(size: 40, weight: .semibold)))

            Text("Camille Laurent")
                .font(.system(size: 24, weight: .semibold))

            Text(timerInterval: started...Date.distantFuture, countsDown: false)
                .font(.system(size: 15, weight: .medium).monospacedDigit())
                .foregroundStyle(.secondary)

            HStack(spacing: 26) {
                control(symbol: muted ? "mic.slash.fill" : "mic.fill", title: muted ? "Unmute" : "Mute", active: muted) {
                    muted.toggle()
                }
                control(symbol: "speaker.wave.2.fill", title: route.label) {
                    route = route.next
                }
                control(symbol: "phone.down.fill", title: "End", tint: .red) {
                    Task { await IslandActivityManager.shared.end(taskID: taskID ?? "call") }
                }
            }
            .padding(.top, 10)

            Spacer()
        }
        .padding(.top, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 0.04, green: 0.05, blue: 0.09).ignoresSafeArea())
        .navigationTitle("Call")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func control(symbol: String, title: String, active: Bool = false, tint: Color = .white, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 19, weight: .semibold))
                    .frame(width: 58, height: 58)
                    .background(active ? Color.white : Color.white.opacity(0.14), in: Circle())
                    .foregroundStyle(active ? Color.black : tint)
                Text(title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
    }
}
