import AVFoundation
import MetalKit

/// AVPlayer owns decoding, audio and the native picture-in-picture layer.
/// The Metal overlay processes SDR video frames only while enhancement is enabled.
final class EnhancedPlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    private var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    private let player: AVPlayer
    private var subtitleCapture: PlayerSubtitleCapture?

    private var itemObservation: NSKeyValueObservation?
    private var notifications: [NSObjectProtocol] = []
    private var item: AVPlayerItem?
    private var output: AVPlayerItemVideoOutput?
    private var itemTask: Task<Void, Never>?
    private var itemGeneration = 0
    private var subtitleGroup: AVMediaSelectionGroup?
    private var renderer: RawMetalRenderer?
    private var metalView: MTKView?
    private var mode = 0
    private var aspect = 0
    private var request = 0
    private var closed = false
    private var preparedRenderers: [Int: RawMetalRenderer] = [:]
    private var preparingModes: Set<Int> = []
    private let onUnavailable: () -> Void
    private let preparationQueue = DispatchQueue(label: "org.animeko.enhancement.prepare", qos: .userInitiated)

    init(player: AVPlayer, onUnavailable: @escaping () -> Void) {
        self.player = player
        self.onUnavailable = onUnavailable
        super.init(frame: .zero)
        backgroundColor = .black
        playerLayer.player = player
        itemObservation = player.observe(\.currentItem, options: [.initial, .new]) { [weak self] _, _ in
            self?.attachItem()
        }
        let center = NotificationCenter.default
        notifications.append(center.addObserver(forName: AVPlayerItem.mediaSelectionDidChangeNotification, object: nil, queue: .main) { [weak self] notification in
            guard let self, let changedItem = notification.object as? AVPlayerItem,
                  changedItem === self.item else { return }
            self.checkSubtitles()
        })
        notifications.append(center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.metalView?.isPaused = true
        })
        notifications.append(center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            self.metalView?.isPaused = self.mode == 0
        })
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func setSubtitleCapture(_ enabled: Bool, onText: @escaping (String) -> Void) {
        guard !closed else { return }
        if subtitleCapture == nil { subtitleCapture = PlayerSubtitleCapture(player: player) }
        subtitleCapture?.update(enabled: enabled, onText: onText)
    }

    func setSubtitleDelay(_ delay: Double) {
        subtitleCapture?.setDelay(delay)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        metalView?.contentScaleFactor = window?.screen.scale ?? UIScreen.main.scale
        metalView?.frame = bounds
    }

    private func detachItem() {
        itemGeneration += 1
        itemTask?.cancel()
        itemTask = nil
        subtitleGroup = nil
        // Removing the surface also removes its last presented drawable.
        metalView?.isPaused = true
        metalView?.delegate = nil
        metalView?.removeFromSuperview()
        metalView = nil
        if let output, let item { item.remove(output) }
        output = nil
        item = nil
        renderer?.output = nil
        renderer?.clearFrame()
    }

    private func attachItem() {
        detachItem()
        guard mode != 0, renderer != nil, let item = player.currentItem else { return }
        self.item = item
        let generation = itemGeneration
        itemTask = Task { @MainActor [weak self] in
            do {
                let group = try await item.asset.loadMediaSelectionGroup(for: .legible)
                let tracks = try await item.asset.loadTracks(withMediaType: .video)
                let transform = try await tracks.first?.load(.preferredTransform) ?? .identity
                guard let self, !Task.isCancelled, self.itemGeneration == generation, self.item === item else { return }
                self.subtitleGroup = group
                guard !self.checkSubtitles() else { return }
                self.renderer?.presentationTransform = transform
                self.connectOutput(item)
            } catch {
                guard let self, !Task.isCancelled, self.itemGeneration == generation else { return }
                self.fallback(error.localizedDescription)
            }
        }
    }

    @discardableResult
    private func checkSubtitles() -> Bool {
        guard let item, let group = subtitleGroup,
              item.currentMediaSelection.selectedMediaOption(in: group) != nil else { return false }
        fallback("Selected subtitle track requires native rendering")
        return true
    }

    private func connectOutput(_ item: AVPlayerItem) {
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferMetalCompatibilityKey as String: true,
        ])
        // Native rendering remains available for AVPlayerLayer picture-in-picture.
        output.suppressesPlayerRendering = false
        item.add(output)
        self.item = item
        self.output = output
        renderer?.output = output
        createSurface()
    }

    func update(mode: Int, aspect: Int) {
        guard !closed else { return }
        self.aspect = aspect
        playerLayer.videoGravity = aspect == 1 ? .resize : aspect == 2 ? .resizeAspectFill : .resizeAspect
        renderer?.aspectMode = aspect
        guard self.mode != mode else { return }
        request += 1
        let currentRequest = request
        self.mode = mode
        stopEnhancement()
        guard mode != 0 else { return }
        if let prepared = preparedRenderers[mode] {
            activate(prepared, mode: mode, request: currentRequest)
            return
        }
        guard preparingModes.insert(mode).inserted else { return }
        // Shader compilation does not run in UIKit's update/draw transaction.
        preparationQueue.async { [weak self] in
            let result = Result { () throws -> RawMetalRenderer in
                guard let device = MTLCreateSystemDefaultDevice() else {
                    throw NSError(domain: "Metal unavailable", code: 1)
                }
                return try RawMetalRenderer(device: device, quality: mode == 2)
            }
            DispatchQueue.main.async { [weak self] in
                guard let self, !self.closed else { return }
                self.preparingModes.remove(mode)
                switch result {
                case .success(let renderer):
                    self.preparedRenderers[mode] = renderer
                    if self.mode == mode { self.activate(renderer, mode: mode, request: self.request) }
                case .failure(let error):
                    if self.mode == mode { self.fallback(error.localizedDescription) }
                }
            }
        }
    }

    private func activate(_ renderer: RawMetalRenderer, mode: Int, request: Int) {
        self.renderer = renderer
        renderer.failure = nil
        renderer.performanceEnabled = true
        renderer.qualityEnabled = mode == 2
        renderer.aspectMode = aspect
        renderer.onFailure = { [weak self] message in
            DispatchQueue.main.async {
                guard let self, !self.closed, self.request == request else { return }
                self.fallback(message)
            }
        }
        renderer.onEnhancedFrame = { [weak self] renderedMode in
            guard let self, self.request == request else { return }
            NSLog("Video enhancement mode %d: first enhanced frame completed", renderedMode)
            self.renderer?.onEnhancedFrame = nil
        }
        attachItem()
    }

    private func createSurface() {
        guard let renderer, let device = MTLCreateSystemDefaultDevice() else { return }
        let view = MTKView(frame: bounds, device: device)
        view.framebufferOnly = true
        view.isOpaque = false
        view.backgroundColor = .clear
        view.clearColor = MTLClearColorMake(0, 0, 0, 0)
        view.contentScaleFactor = window?.screen.scale ?? UIScreen.main.scale
        view.preferredFramesPerSecond = 60
        view.delegate = renderer
        view.isUserInteractionEnabled = false
        view.isPaused = UIApplication.shared.applicationState == .background
        metalView = view
        addSubview(view)
    }

    private func stopEnhancement() {
        renderer?.onFailure = nil
        renderer?.onEnhancedFrame = nil
        detachItem()
        renderer = nil
    }

    private func fallback(_ message: String) {
        NSLog("Video enhancement stopped; native playback active: %@", message)
        request += 1
        mode = 0
        stopEnhancement()
        onUnavailable()
    }

    func close() {
        guard !closed else { return }
        closed = true
        subtitleCapture?.close()
        subtitleCapture = nil
        request += 1
        itemObservation?.invalidate()
        itemObservation = nil
        notifications.forEach { NotificationCenter.default.removeObserver($0) }
        notifications.removeAll()
        stopEnhancement()
        preparedRenderers.removeAll()
        playerLayer.player = nil
    }

    deinit { close() }
}

/// Captures decoded text while the player's media selection remains authoritative.
private final class PlayerSubtitleCapture: NSObject, AVPlayerItemLegibleOutputPushDelegate {
    private let player: AVPlayer
    private var observation: NSKeyValueObservation?
    private var selectionObserver: NSObjectProtocol?
    private var item: AVPlayerItem?
    private var output: AVPlayerItemLegibleOutput?
    private var onText: ((String) -> Void)?
    private var text = ""
    private var enabled = false
    private var hasTextSamples = false
    private var delaySeconds = 0.0
    private var samples: [(time: Double, text: String)] = []
    private var timeObserver: Any?

    init(player: AVPlayer) {
        self.player = player
        super.init()
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.03, preferredTimescale: 1000), queue: .main) { [weak self] _ in
            self?.displayCurrentText()
        }
        observation = player.observe(\.currentItem, options: [.initial, .new]) { [weak self] _, _ in
            self?.attach()
        }
        selectionObserver = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.mediaSelectionDidChangeNotification, object: nil, queue: .main
        ) { [weak self] notification in
            guard let self, let changed = notification.object as? AVPlayerItem, changed === self.item else { return }
            self.text = ""
            self.samples.removeAll()
            self.hasTextSamples = false
            self.output?.suppressesPlayerRendering = false
            self.onText?("")
        }
    }

    func update(enabled: Bool, onText: @escaping (String) -> Void) {
        self.onText = onText
        self.enabled = enabled
        output?.suppressesPlayerRendering = enabled && hasTextSamples
        displayCurrentText()
    }

    func setDelay(_ delay: Double) {
        guard delay != delaySeconds else { return }
        delaySeconds = delay
        output?.advanceIntervalForDelegateInvocation = max(0, -delay) + 1
        displayCurrentText()
    }

    private func displayCurrentText() {
        let position = player.currentTime().seconds - delaySeconds
        text = samples.last(where: { $0.time <= position })?.text ?? ""
        onText?(enabled ? text : "")
    }

    private func attach() {
        if let item, let output { item.remove(output) }
        text = ""
        samples.removeAll()
        hasTextSamples = false
        onText?("")
        item = player.currentItem
        output = nil
        guard let item else { return }
        let output = AVPlayerItemLegibleOutput(mediaSubtypesForNativeRepresentation: [])
        output.setDelegate(self, queue: .main)
        output.advanceIntervalForDelegateInvocation = max(0, -delaySeconds) + 1
        output.suppressesPlayerRendering = false
        item.add(output)
        self.output = output
    }

    func legibleOutput(_ output: AVPlayerItemLegibleOutput,
                       didOutputAttributedStrings strings: [NSAttributedString],
                       nativeSampleBuffers nativeSamples: [Any], forItemTime itemTime: CMTime) {
        guard output === self.output else { return }
        let sampleText = strings.map(\.string).joined(separator: "\n")
        let timestamp = itemTime.seconds
        guard timestamp.isFinite else { return }
        samples.removeAll { $0.time == timestamp || $0.time < player.currentTime().seconds - 605 }
        samples.append((timestamp, sampleText))
        samples.sort { $0.time < $1.time }
        if !sampleText.isEmpty { hasTextSamples = true }
        output.suppressesPlayerRendering = enabled && hasTextSamples
        displayCurrentText()
    }

    func outputSequenceWasFlushed(_ output: AVPlayerItemOutput) {
        guard output === self.output else { return }
        text = ""
        samples.removeAll()
        onText?("")
    }

    func close() {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        observation?.invalidate()
        observation = nil
        if let selectionObserver { NotificationCenter.default.removeObserver(selectionObserver) }
        selectionObserver = nil
        output?.suppressesPlayerRendering = false
        if let item, let output { item.remove(output) }
        output?.setDelegate(nil, queue: nil)
        output = nil
        item = nil
        onText = nil
    }
}
