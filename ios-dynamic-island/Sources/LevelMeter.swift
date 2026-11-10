import AVFoundation
import Foundation

/// Mic level → five normalised buckets, throttled to ~6 Hz and smoothed with a
/// fast attack / slow release curve so the island bars feel like audio, not noise.
final class LevelMeter {
    private let engine = AVAudioEngine()
    private var smoothed: Double = 0
    private var lastPush = Date.distantPast

    /// Called on the audio thread. Hop to the main actor before touching ActivityKit.
    var onLevels: (([Double]) -> Void)?

    func start() {
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else { return }

        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            guard let self else { return }
            let level = Self.rms(of: buffer)
            self.smoothed = level > self.smoothed
                ? self.smoothed + (level - self.smoothed) * 0.6
                : self.smoothed * 0.86

            let now = Date()
            guard now.timeIntervalSince(self.lastPush) > 0.16 else { return }
            self.lastPush = now
            self.onLevels?(Self.buckets(from: self.smoothed))
        }

        do {
            try engine.start()
        } catch {
            // The call still works without the visualizer; the widget falls back
            // to the last levels in the payload.
            engine.stop()
        }
    }

    func stop() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
    }

    private static func rms(of buffer: AVAudioPCMBuffer) -> Double {
        guard let channel = buffer.floatChannelData?[0] else { return 0 }
        let frames = Int(buffer.frameLength)
        guard frames > 0 else { return 0 }

        var sum: Float = 0
        for index in 0..<frames {
            let sample = channel[index]
            sum += sample * sample
        }
        let rms = sqrt(Double(sum) / Double(frames))
        return min(1, rms * 6)
    }

    private static func buckets(from level: Double) -> [Double] {
        [0.55, 0.85, 1.0, 0.72, 0.6].map { min(1, level * $0 * 1.5) }
    }
}
