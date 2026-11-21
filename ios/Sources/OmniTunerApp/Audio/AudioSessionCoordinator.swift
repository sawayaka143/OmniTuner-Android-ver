import AVFoundation

public enum AudioSessionCoordinator {
    public static func configureForPlayback() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true)
        } catch {
            // Best-effort configuration; playback engines still attempt to start.
        }
    }

    public static func configureForTunerCapture() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try session.setPreferredSampleRate(48000)
            try session.setPreferredIOBufferDuration(0.02)
            try session.setActive(true)
        } catch {
            // Fallback to default playAndRecord if measurement mode fails on a route.
            try? session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try? session.setActive(true)
        }
    }
}
