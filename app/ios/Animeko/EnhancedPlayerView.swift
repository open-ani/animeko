import AVFoundation
import MetalKit

/// AVPlayer owns decoding, audio and the native picture-in-picture layer.
/// The Metal overlay processes SDR video frames only while enhancement is enabled.
final class EnhancedPlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    private var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    private let player: AVPlayer
    private var itemObservation: NSKeyValueObservation?
    private var notifications: [NSObjectProtocol] = []
    private var item: AVPlayerItem?
    private var output: AVPlayerItemVideoOutput?
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
        notifications.append(center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.metalView?.isPaused = true
        })
        notifications.append(center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            self.metalView?.isPaused = self.mode == 0
        })
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        metalView?.contentScaleFactor = window?.screen.scale ?? UIScreen.main.scale
        metalView?.frame = bounds
    }

    private func detachItem() {
        if let output, let item { item.remove(output) }
        output = nil
        item = nil
        renderer?.output = nil
        renderer?.clearFrame()
    }

    private func attachItem() {
        detachItem()
        guard mode != 0, renderer != nil, let item = player.currentItem else { return }
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
        guard let device = MTLCreateSystemDefaultDevice() else { fallback("Metal unavailable"); return }
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
        attachItem()
        addSubview(view)
    }

    private func stopEnhancement() {
        metalView?.isPaused = true
        metalView?.delegate = nil
        metalView?.removeFromSuperview()
        metalView = nil
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
