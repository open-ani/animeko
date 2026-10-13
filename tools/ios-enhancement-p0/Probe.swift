import AVFoundation
import AVKit
import CoreImage
import Metal
import MetalKit
import SwiftUI

@MainActor
final class CompositionRequest {
    var continuation: CheckedContinuation<AVVideoComposition, Error>?
    func finish(_ result: Result<AVVideoComposition, Error>) {
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(with: result)
    }
}

@main
struct ProbeApp: App {
    @StateObject private var probe = Probe()
    var body: some Scene {
        WindowGroup {
            VStack {
                Text("AVVideoComposition P0").font(.headline)
                ZStack {
                    PlayerSurface(probe: probe)
                    MetalSurface(probe: probe).opacity(probe.metalEnabled ? 1 : 0)
                }.frame(height: 220)
                HStack {
                    Button("Run P0") { Task { await probe.run() } }.disabled(probe.running)
                    Button("HLS frames") { Task { await probe.runHlsOutput() } }.disabled(probe.running)
                    Button("PiP") { probe.startPiP() }
                }
                HStack {
                    Button("Metal P0") { Task { await probe.runMetalOutput() } }.disabled(probe.running)
                    Button(probe.metalEnabled ? "Native output" : "Metal output") { probe.toggleMetal() }.disabled(probe.running)
                }
                HStack {
                    Button("Anime4K P1") { Task { await probe.runAnime4k() } }.disabled(probe.running)
                    Button("Both modes") { Task { await probe.runBothModes() } }.disabled(probe.running)
                    Button(probe.enhancementEnabled ? "Enhancement off" : "Enhancement on") { probe.toggleEnhancement() }.disabled(probe.running)
                    Button("Play / Pause") {
                        if probe.player.rate == 0 { probe.player.play() } else { probe.player.pause() }
                    }
                }
                if let frame = probe.lastFrame {
                    Image(uiImage: frame).resizable().scaledToFit().frame(height: 120)
                }
                ScrollView { Text(probe.report).font(.system(.caption, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading) }
            }.padding().task { await probe.run() }
        }
    }
}

@MainActor
final class Probe: NSObject, ObservableObject, @preconcurrency AVPictureInPictureControllerDelegate {
    let player = AVPlayer()
    @Published var report = ""
    @Published var running = false
    @Published var lastFrame: UIImage?
    @Published var metalEnabled = false
    @Published var enhancementEnabled = false
    var renderer: RawMetalRenderer?
    weak var metalView: ProbeMetalView?
    private var metalOutput: AVPlayerItemVideoOutput?
    private var pip: AVPictureInPictureController?

    func record(_ line: String) {
        report += line + "\n"
        print("P0: " + line)
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("p0-report.txt")
        try? report.write(to: url, atomically: true, encoding: .utf8)
    }

    func attach(_ layer: AVPlayerLayer) {
        pip = AVPictureInPictureController(playerLayer: layer)
        pip?.delegate = self
    }

    func startPiP() {
        record("PiP possible=\(pip?.isPictureInPicturePossible ?? false)")
        pip?.startPictureInPicture()
    }

    func pictureInPictureControllerDidStartPictureInPicture(_ controller: AVPictureInPictureController) { record("PASS PiP started") }
    func pictureInPictureController(_ controller: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) { record("FAIL PiP: \(error)") }
    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) { record("PiP stopped") }
    func pictureInPictureController(_ controller: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) { completionHandler(true) }

    func run() async {
        player.isMuted = CommandLine.arguments.contains("--muted")
        if CommandLine.arguments.contains("--review-fixes") { await runReviewFixes(); return }
        if CommandLine.arguments.contains("--regressions") {
            await runBothModes()
            let bothModesReport = report
            await runAnime4k()
            let performanceReport = report
            report = bothModesReport + "\nREPEAT PERFORMANCE AFTER QUALITY\n" + performanceReport
            record("DONE regression sequence")
            return
        }
        if CommandLine.arguments.contains("--both-modes") { await runBothModes(); return }
        if CommandLine.arguments.contains("--anime4k") {
            await runAnime4k()
            return
        }
        if CommandLine.arguments.contains("--metal-output") {
            await runMetalOutput()
            return
        }
        if CommandLine.arguments.contains("--hls-output") {
            await runHlsOutput()
            return
        }
        guard !running else { return }
        running = true
        defer { running = false }
        report = ""
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
            let fixture = try await makeFixture()
            await exercise("local MP4", url: fixture)
            await exercise("HTTPS MP4", url: URL(string: "https://media.w3.org/2010/05/bunny/trailer.mp4")!)
            await exercise("HLS", url: URL(string: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8")!)
            record("DONE. PiP and visual checks require observation. PikPak not tested.")
        } catch { record("FAIL setup: \(error)") }
    }

    func toggleMetal() {
        guard let output = metalOutput else { return }
        metalEnabled.toggle()
        output.suppressesPlayerRendering = metalEnabled
        renderer?.output = metalEnabled ? output : nil
        renderer?.clearFrame()
    }

    func toggleEnhancement() {
        enhancementEnabled.toggle()
        renderer?.performanceEnabled = enhancementEnabled
    }

    func runAnime4k() async {
        defer { player.pause() }
        await runMetalOutput()
        guard let renderer, renderer.failure == nil else { return }
        running = true
        defer { player.pause(); running = false }
        record("START Anime4K Restore CNN S, four Metal compute passes")
        renderer.qualityEnabled = false
        enhancementEnabled = true
        renderer.performanceEnabled = true
        player.play()
        let count = renderer.completedEnhancedFrames.value
        let worked = await waitUntil { renderer.completedEnhancedFrames.value >= count + 60 || renderer.failure != nil }
        record("\(worked && renderer.failure == nil ? "PASS" : "FAIL") enhanced GPU frames=\(renderer.completedEnhancedFrames.value - count); error=\(renderer.failure ?? "none")")
        let start = CACurrentMediaTime()
        let before = renderer.completedEnhancedFrames.value
        try? await Task.sleep(nanoseconds: 3_000_000_000)
        let elapsed = CACurrentMediaTime() - start
        record("enhanced observed new-frame rate=\(Double(renderer.completedEnhancedFrames.value - before) / elapsed) fps; GPU max=\(renderer.maxGpuMilliseconds) ms")
        toggleEnhancement()
        let rawCount = renderer.completedFrames.value
        let off = await waitUntil { renderer.completedFrames.value >= rawCount + 15 }
        record("\(off ? "PASS" : "FAIL") enhancement off keeps raw Metal playback")
        toggleEnhancement()
        let onCount = renderer.completedEnhancedFrames.value
        let on = await waitUntil { renderer.completedEnhancedFrames.value >= onCount + 15 }
        record("\(on ? "PASS" : "FAIL") enhancement on resumes compute")
        player.pause()
        try? await Task.sleep(nanoseconds: 300_000_000)
        record(renderer.comparePausedFrame())
        record("DONE paused. Use Play and Enhancement buttons for visual comparison; quality, HDR and long-session power unverified.")
    }

    func runBothModes() async {
        defer { player.pause() }
        await runMetalOutput()
        guard let renderer, renderer.failure == nil else { return }
        running = true
        defer { running = false }
        for quality in [false, true] {
            record("START \(quality ? "QUALITY Restore M + Upscale x2 M" : "PERFORMANCE Restore S") + EWA")
            renderer.qualityEnabled = quality
            renderer.performanceEnabled = true
            enhancementEnabled = true
            player.play()
            renderer.maxGpuMilliseconds = 0
            let before = renderer.completedEnhancedFrames.value
            let worked = await waitUntil { renderer.completedEnhancedFrames.value >= before + 30 || renderer.failure != nil }
            guard worked && renderer.failure == nil else { record("FAIL mode: \(renderer.failure ?? "frame timeout")"); return }
            let start = CACurrentMediaTime()
            let count = renderer.completedEnhancedFrames.value
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            let fps = Double(renderer.completedEnhancedFrames.value - count) / (CACurrentMediaTime() - start)
            record("MODE sampled fps=\(fps), max GPU ms=\(renderer.maxGpuMilliseconds)")
            record("\(fps >= 50 ? "PASS" : "FAIL") 60fps sample target (50fps minimum for this probe)")
            player.pause()
            try? await Task.sleep(nanoseconds: 300_000_000)
            if !quality { record(renderer.comparePausedFrame()) }
            if quality {
                record("\(renderer.comparePausedFrame().contains("requires matching dimensions") ? "PASS" : "FAIL") mismatched comparison dimensions rejected")
            }
            let presentations = renderer.completedPresentations.value
            try? await Task.sleep(nanoseconds: 500_000_000)
            record("\(renderer.completedPresentations.value == presentations ? "PASS" : "FAIL") paused frame submits no repeated GPU presentation")
            toggleEnhancement()
            record("\(renderer.allocatedTextureBytes == 0 ? "PASS" : "FAIL") off releases enhancement textures")
            player.play()
            let raw = renderer.completedFrames.value
            let off = await waitUntil { renderer.completedFrames.value >= raw + 15 }
            record("\(off ? "PASS" : "FAIL") mode off")
            toggleEnhancement()
            let onCount = renderer.completedEnhancedFrames.value
            let on = await waitUntil { renderer.completedEnhancedFrames.value >= onCount + 15 }
            record("\(on ? "PASS" : "FAIL") mode on")
        }
        if let view = metalView {
            player.pause()
            try? await Task.sleep(nanoseconds: 300_000_000)
            view.withholdDrawable = true
            let before = renderer.lastFrameTime
            let target = max(0, before - 1)
            _ = await player.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
            let acquired = await waitUntil { abs(renderer.lastFrameTime - before) > 0.1 }
            let completed = renderer.completedEnhancedFrames.value
            view.withholdDrawable = false
            let presented = await waitUntil { renderer.completedEnhancedFrames.value > completed }
            record("\(acquired && presented ? "PASS" : "FAIL") paused new frame survives unavailable drawable")
        }
        record("DONE both modes paused. Short probe only; production integration conditional on results.")
    }

    func runMetalOutput() async {
        guard !running else { return }
        running = true
        defer { running = false }
        report = ""
        lastFrame = nil
        guard await waitUntil({ self.renderer != nil }), let renderer else {
            record("FAIL Metal view not created")
            return
        }
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
            let item = AVPlayerItem(url: URL(string: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8")!)
            let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferMetalCompatibilityKey as String: true])
            if let previous = metalOutput { player.currentItem?.remove(previous) }
            metalOutput = output
            item.add(output)
            output.suppressesPlayerRendering = true
            metalEnabled = true
            renderer.output = output
            renderer.clearFrame()
            player.replaceCurrentItem(with: item)
            player.play()
            record("START HLS Metal raw output; native video rendering suppressed")
            guard await waitUntil({ item.status != .unknown }), item.status == .readyToPlay else { record("FAIL ready: \(String(describing: item.error))"); return }
            let initialCount = renderer.completedFrames.value
            let frames = await waitUntil { renderer.completedFrames.value >= initialCount + 30 }
            record("\(frames ? "PASS" : "FAIL") Metal GPU completed=\(renderer.completedFrames.value), renderer error=\(renderer.failure ?? "none")")
            guard frames else { return }
            record("frame timestamp offset max=\(renderer.maxTimestampOffset) seconds; NOT measured audio sync")
            for target in [10.0, 2.0] {
                renderer.output = nil
                renderer.clearFrame()
                let sought = await player.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
                renderer.output = output
                let count = renderer.completedFrames.value
                let resumed = await waitUntil { renderer.completedFrames.value >= count + 15 }
                record("\(sought && resumed && abs(renderer.lastFrameTime - target) < 3 ? "PASS" : "FAIL") seek \(target)s rendered PTS=\(renderer.lastFrameTime)")
            }
            player.pause()
            try await Task.sleep(nanoseconds: 300_000_000)
            let position = player.currentTime().seconds
            let frameTime = renderer.lastFrameTime
            try await Task.sleep(nanoseconds: 500_000_000)
            record("\(abs(player.currentTime().seconds - position) < 0.1 && renderer.lastFrameTime == frameTime ? "PASS" : "FAIL") pause holds last frame")
            player.play()
            let count = renderer.completedFrames.value
            let resumed = await waitUntil { renderer.completedFrames.value >= count + 15 && renderer.lastFrameTime > frameTime }
            record("\(resumed ? "PASS" : "FAIL") resume")
            toggleMetal()
            let offPosition = player.currentTime().seconds
            try await Task.sleep(nanoseconds: 700_000_000)
            record("\(!output.suppressesPlayerRendering && player.currentTime().seconds > offPosition ? "PASS" : "FAIL") switch to native without replacing player item")
            toggleMetal()
            let onCount = renderer.completedFrames.value
            let enabled = await waitUntil { renderer.completedFrames.value >= onCount + 15 }
            record("\(enabled ? "PASS" : "FAIL") switch back to Metal")
            record("DONE Metal remains active. Observe moving picture and sound; HDR, enhanced PiP and color fidelity unverified.")
        } catch { record("FAIL Metal output: \(error)") }
    }

    func runHlsOutput() async {
        guard !running else { return }
        running = true
        defer { running = false }
        report = ""
        lastFrame = nil
        record("START HLS AVPlayerItemVideoOutput")
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
            let item = AVPlayerItem(url: URL(string: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8")!)
            let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferMetalCompatibilityKey as String: true,
            ])
            item.add(output)
            defer { item.remove(output) }
            player.replaceCurrentItem(with: item)
            player.play()
            guard await waitUntil({ item.status != .unknown }), item.status == .readyToPlay else {
                record("FAIL HLS baseline: \(String(describing: item.error))")
                return
            }
            let initial = await collectFrames(output)
            record("\(initial >= 10 ? "PASS" : "FAIL") HLS decoded frames=\(initial)")
            guard initial >= 10 else { return }
            let sought = await player.seek(to: CMTime(seconds: 10, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
            let afterSeek = await collectFrames(output)
            record("\(sought && afterSeek >= 10 ? "PASS" : "FAIL") HLS seek frames=\(afterSeek)")
            player.pause()
            let pausedTime = player.currentTime().seconds
            try await Task.sleep(nanoseconds: 400_000_000)
            record("\(abs(player.currentTime().seconds - pausedTime) < 0.1 ? "PASS" : "FAIL") HLS pause")
            player.play()
            let afterResume = await collectFrames(output)
            record("\(afterResume >= 10 ? "PASS" : "FAIL") HLS resume frames=\(afterResume)")
            if pip?.isPictureInPicturePossible == true {
                startPiP()
                let started = await waitUntil { self.pip?.isPictureInPictureActive == true }
                let pipFrames = await collectFrames(output)
                record("\(started ? "PASS" : "FAIL") ORIGINAL HLS PiP active; pulled frames=\(pipFrames)")
                pip?.stopPictureInPicture()
                _ = await waitUntil { self.pip?.isPictureInPictureActive == false }
            } else { record("UNVERIFIED original HLS PiP unavailable") }
            record("DONE. Preview uses decoded frames. Original PiP is NOT an enhanced PiP renderer.")
        } catch { record("FAIL HLS output: \(error)") }
    }

    private func collectFrames(_ output: AVPlayerItemVideoOutput) async -> Int {
        var count = 0
        let context = CIContext()
        for _ in 0..<300 {
            let time = output.itemTime(forHostTime: CACurrentMediaTime())
            if output.hasNewPixelBuffer(forItemTime: time),
               let buffer = output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil) {
                count += 1
                if count == 1 {
                    record("frame \(CVPixelBufferGetWidth(buffer))x\(CVPixelBufferGetHeight(buffer)) format=\(CVPixelBufferGetPixelFormatType(buffer))")
                    if let device = MTLCreateSystemDefaultDevice() {
                        var cache: CVMetalTextureCache?
                        let cacheStatus = CVMetalTextureCacheCreate(nil, nil, device, nil, &cache)
                        if cacheStatus == kCVReturnSuccess, let cache {
                            var metalTexture: CVMetalTexture?
                            let status = CVMetalTextureCacheCreateTextureFromImage(nil, cache, buffer, nil, .bgra8Unorm, CVPixelBufferGetWidth(buffer), CVPixelBufferGetHeight(buffer), 0, &metalTexture)
                            let usable = status == kCVReturnSuccess && metalTexture.flatMap(CVMetalTextureGetTexture) != nil
                            record("\(usable ? "PASS" : "FAIL") decoded frame as Metal texture status=\(status)")
                        } else { record("FAIL Metal texture cache status=\(cacheStatus)") }
                    } else { record("UNVERIFIED Metal device unavailable") }
                    let image = CIImage(cvPixelBuffer: buffer)
                    if let cgImage = context.createCGImage(image, from: image.extent) { lastFrame = UIImage(cgImage: cgImage) }
                }
                if count >= 10 { break }
            }
            try? await Task.sleep(nanoseconds: 33_000_000)
        }
        return count
    }

    private func exercise(_ name: String, url: URL) async {
        record("START \(name)")
        let item = AVPlayerItem(url: url)
        player.replaceCurrentItem(with: item)
        player.play()
        let ready = await waitUntil { item.status != .unknown }
        guard ready && item.status == .readyToPlay else { record("FAIL baseline \(name): \(String(describing: item.error))"); return }
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        record("baseline time=\(player.currentTime().seconds)")
        let counter = FrameCounter()
        do {
            let composition = try await withCheckedThrowingContinuation { continuation in
                let pending = CompositionRequest()
                pending.continuation = continuation
                AVVideoComposition.videoComposition(with: item.asset, applyingCIFiltersWithHandler: { request in
                    counter.increment()
                    request.finish(with: request.sourceImage, context: nil)
                }, completionHandler: { composition, error in
                    Task { @MainActor in
                        if let composition { pending.finish(.success(composition)) }
                        else { pending.finish(.failure(error ?? NSError(domain: "P0", code: 1, userInfo: [NSLocalizedDescriptionKey: "AVFoundation returned nil composition without an error"]))) }
                    }
                })
                DispatchQueue.main.asyncAfter(deadline: .now() + 15) {
                    pending.finish(.failure(NSError(domain: "P0 composition creation timeout", code: 2)))
                }
            }
            item.videoComposition = composition
            let processed = await waitUntil { counter.value >= 10 || item.status == .failed }
            record("\(processed && counter.value >= 10 ? "PASS" : "FAIL") \(name) passthrough callbacks=\(counter.value) time=\(player.currentTime().seconds) error=\(String(describing: item.error))")
            guard counter.value >= 10 else { return }
            let before = counter.value
            let sought = await player.seek(to: CMTime(seconds: 4, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
            let resumed = await waitUntil { counter.value >= before + 5 }
            record("\(sought && resumed ? "PASS" : "FAIL") \(name) seek callbacks=\(counter.value)")
            item.videoComposition = nil
            let offTime = player.currentTime().seconds
            try? await Task.sleep(nanoseconds: 700_000_000)
            record("\(player.currentTime().seconds > offTime ? "PASS" : "FAIL") \(name) disable keeps playback")
            item.videoComposition = composition
            let onCount = counter.value
            let enabled = await waitUntil { counter.value >= onCount + 5 }
            record("\(enabled ? "PASS" : "FAIL") \(name) re-enable")
            record("\(name) PiP possible=\(pip?.isPictureInPicturePossible ?? false)")
            if name == "local MP4", pip?.isPictureInPicturePossible == true {
                startPiP()
                let started = await waitUntil { self.pip?.isPictureInPictureActive == true }
                record("\(started ? "PASS" : "FAIL") local composition PiP active")
                pip?.stopPictureInPicture()
                _ = await waitUntil { self.pip?.isPictureInPictureActive == false }
            }
        } catch { record("FAIL \(name) composition creation: \(error)") }
    }

    private func waitUntil(_ predicate: () -> Bool) async -> Bool {
        for _ in 0..<100 {
            if predicate() { return true }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
        return false
    }

    func runReviewFixes() async {
        running = true
        defer { player.pause(); running = false }
        let transforms: [(String, CGAffineTransform, SIMD2<Float>)] = [
            ("identity", .identity, SIMD2(-1, 1)),
            ("90 degrees", CGAffineTransform(a: 0, b: 1, c: -1, d: 0, tx: 180, ty: 0), SIMD2(1, 1)),
            ("180 degrees", CGAffineTransform(a: -1, b: 0, c: 0, d: -1, tx: 320, ty: 180), SIMD2(1, -1)),
            ("270 degrees", CGAffineTransform(a: 0, b: -1, c: 1, d: 0, tx: 0, ty: 320), SIMD2(-1, -1)),
            ("mirror", CGAffineTransform(a: -1, b: 0, c: 0, d: 1, tx: 320, ty: 0), SIMD2(1, 1)),
        ]
        for (name, transform, expected) in transforms {
            let vertices = videoPresentationVertices(sourceSize: CGSize(width: 320, height: 180), targetSize: CGSize(width: 200, height: 200), transform: transform, aspectMode: 1)
            let first = vertices[0]
            record("\(abs(first.x - expected.x) < 0.001 && abs(first.y - expected.y) < 0.001 && first.z == 0 && first.w == 0 ? "PASS" : "FAIL") orientation \(name)")
        }
        for mode in [0, 2] {
            let vertices = videoPresentationVertices(sourceSize: CGSize(width: 320, height: 180), targetSize: CGSize(width: 200, height: 200), transform: transforms[1].1, aspectMode: mode)
            let expectedX: Float = mode == 0 ? 180.0 / 320.0 : 1
            let expectedY: Float = mode == 0 ? 1 : 320.0 / 180.0
            record("\(abs(vertices[0].x - expectedX) < 0.001 && abs(vertices[0].y - expectedY) < 0.001 ? "PASS" : "FAIL") rotated aspect mode \(mode)")
        }
        var fallbacks = 0
        _ = await waitUntil { self.metalView?.window != nil }
        let view = EnhancedPlayerView(player: player) { fallbacks += 1 }
        view.frame = CGRect(x: 0, y: 0, width: 320, height: 180)
        metalView?.window?.addSubview(view)
        defer { view.close(); view.removeFromSuperview() }
        func surface() -> MTKView? { view.subviews.compactMap { $0 as? MTKView }.first }
        do {
            let fixture = try await makeFixture(transform: transforms[1].1)
            defer { try? FileManager.default.removeItem(at: fixture) }
            player.replaceCurrentItem(with: AVPlayerItem(url: fixture))
            view.update(mode: 1, aspect: 0)
            player.play()
            let attached = await waitUntil { surface() != nil || fallbacks > 0 }
            let oldSurface = surface()
            let productionRenderer = oldSurface?.delegate as? RawMetalRenderer
            let rendered = await waitUntil { (productionRenderer?.completedEnhancedFrames.value ?? 0) > 0 }
            record("rotated fixture: window=\(view.window != nil), attached=\(attached), frames=\(productionRenderer?.completedEnhancedFrames.value ?? 0), transform=\(String(describing: productionRenderer?.presentationTransform)), error=\(productionRenderer?.failure ?? "none")")
            record("\(attached && rendered && productionRenderer?.presentationTransform == transforms[1].1 ? "PASS" : "FAIL") rotated asset metadata and enhanced GPU frame")
            player.replaceCurrentItem(with: AVPlayerItem(url: fixture))
            record("\(oldSurface != nil && oldSurface?.superview == nil && surface() == nil ? "PASS" : "FAIL") item replacement immediately removes old drawable surface")
            let replaced = await waitUntil { surface() != nil }
            record("\(replaced && surface() !== oldSurface ? "PASS" : "FAIL") replacement creates a fresh surface")

            let hls = AVPlayerItem(url: URL(string: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8")!)
            view.update(mode: 0, aspect: 0)
            player.replaceCurrentItem(with: hls)
            player.play()
            record("Loading HLS subtitle fixture")
            let group = try await hls.asset.loadMediaSelectionGroup(for: .legible)
            guard let group, let option = group.options.first else {
                record("FAIL subtitle fixture has no selectable legible track")
                return
            }
            hls.select(nil, in: group)
            view.update(mode: 1, aspect: 0)
            let hlsReady = await waitUntil { surface() != nil }
            let before = fallbacks
            hls.select(option, in: group)
            let stopped = await waitUntil { fallbacks > before }
            record("\(hlsReady && stopped && surface() == nil ? "PASS" : "FAIL") selecting subtitles removes overlay and reports native fallback")
            let beforeEnable = fallbacks
            view.update(mode: 1, aspect: 0)
            let rejected = await waitUntil { fallbacks > beforeEnable }
            record("\(rejected && surface() == nil ? "PASS" : "FAIL") already-selected subtitles prevent overlay activation")
        } catch { record("FAIL review fixes: \(error)") }
        record("DONE review fixes")
    }

    private func makeFixture(transform: CGAffineTransform = .identity) async throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mp4")
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 320, AVVideoHeightKey: 180])
        input.transform = transform
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey as String: 320, kCVPixelBufferHeightKey as String: 180])
        writer.add(input)
        guard writer.startWriting() else { throw writer.error! }
        writer.startSession(atSourceTime: .zero)
        let context = CIContext()
        for frame in 0..<360 {
            while !input.isReadyForMoreMediaData { try await Task.sleep(nanoseconds: 1_000_000) }
            var buffer: CVPixelBuffer?
            CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &buffer)
            let image = CIImage(color: CIColor(red: CGFloat(frame % 60) / 60, green: 0.4, blue: 0.7)).cropped(to: CGRect(x: 0, y: 0, width: 320, height: 180))
            context.render(image, to: buffer!)
            guard adaptor.append(buffer!, withPresentationTime: CMTime(value: Int64(frame), timescale: 30)) else { throw writer.error! }
        }
        input.markAsFinished()
        await writer.finishWriting()
        if let error = writer.error { throw error }
        return url
    }
}

@MainActor
final class ProbeMetalView: MTKView {
    var withholdDrawable = false
    override var currentDrawable: CAMetalDrawable? { withholdDrawable ? nil : super.currentDrawable }
}

@MainActor
struct MetalSurface: UIViewRepresentable {
    let probe: Probe
    func makeUIView(context: Context) -> MTKView {
        let device = MTLCreateSystemDefaultDevice()
        let view = ProbeMetalView(frame: .zero, device: device)
        probe.metalView = view
        view.colorPixelFormat = .bgra8Unorm
        view.clearColor = MTLClearColorMake(0, 0, 0, 1)
        view.preferredFramesPerSecond = 60
        do {
            guard let device else { throw NSError(domain: "Metal device unavailable", code: 1) }
            let renderer = try RawMetalRenderer(device: device)
            probe.renderer = renderer
            view.delegate = renderer
        } catch { probe.record("FAIL renderer creation: \(error)") }
        return view
    }
    func updateUIView(_ view: MTKView, context: Context) {}
    static func dismantleUIView(_ view: MTKView, coordinator: ()) { view.isPaused = true; view.delegate = nil }
}

final class PlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
}

struct PlayerSurface: UIViewRepresentable {
    let probe: Probe
    func makeUIView(context: Context) -> PlayerView {
        let view = PlayerView()
        let layer = view.layer as! AVPlayerLayer
        layer.player = probe.player
        layer.videoGravity = .resizeAspect
        probe.attach(layer)
        return view
    }
    func updateUIView(_ view: PlayerView, context: Context) {}
}
