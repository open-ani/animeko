import AVFoundation
import CoreVideo
import MetalKit

final class FrameCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    func increment() { lock.lock(); count += 1; lock.unlock() }
    var value: Int { lock.lock(); defer { lock.unlock() }; return count }
}

final class RawMetalRenderer: NSObject, MTKViewDelegate {
    var presentationTransform = CGAffineTransform.identity { didSet { needsRedraw = true } }
    var aspectMode = 0 { didSet { if oldValue != aspectMode { needsRedraw = true } } } // fit, stretch, crop
    var onEnhancedFrame: ((Int) -> Void)?
    var onFailure: ((String) -> Void)?
    var output: AVPlayerItemVideoOutput?
    let completedFrames = FrameCounter()
    let completedEnhancedFrames = FrameCounter()
    let completedPresentations = FrameCounter()
    var allocatedTextureBytes: Int {
        (intermediateTextures + qualityTextures.flatMap { $0 }).reduce(0) { $0 + $1.width * $1.height * 8 }
    }
    var performanceEnabled = false { didSet { if oldValue != performanceEnabled { releaseTextures() } } }
    var qualityEnabled = false { didSet { if oldValue != qualityEnabled { releaseTextures() } } }
    var maxGpuMilliseconds = 0.0
    var lastFrameTime = 0.0
    var maxTimestampOffset = 0.0
    var failure: String? { didSet { if let failure, oldValue == nil { onFailure?(failure) } } }
    private var needsRedraw = true
    private var generation = 0
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let ewaPipeline: MTLRenderPipelineState
    private let computePipelines: [MTLComputePipelineState]
    private let qualityPasses: [[MetalCnnPass]]
    private var qualityTextures: [[MTLTexture]] = []
    private let device: MTLDevice
    private var intermediateTextures: [MTLTexture] = []
    private var processedTexture: MTLTexture?
    private var cache: CVMetalTextureCache!
    private var frame: (CVPixelBuffer, CVMetalTexture, MTLTexture)?

    // A nil profile prepares both modes for the standalone probe.
    init(device: MTLDevice, quality: Bool? = nil) throws {
        self.device = device
        guard let queue = device.makeCommandQueue() else { throw NSError(domain: "Metal command queue", code: 1) }
        self.queue = queue
        let library = try device.makeLibrary(source: """
        #include <metal_stdlib>
        using namespace metal;
        struct V { float4 position [[position]]; float2 uv; };
        vertex V rawVertex(uint id [[vertex_id]], constant float4 *vertices [[buffer(0)]]) {
            V v; v.position = float4(vertices[id].xy,0,1);
            v.uv = vertices[id].zw; return v;
        }
        fragment float4 rawFragment(V v [[stage_in]], texture2d<float> image [[texture(0)]]) {
            constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);
            return float4(image.sample(s, v.uv).rgb, 1);
        }
        """, options: nil)
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "rawVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "rawFragment")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        let ewaURL = Bundle.main.url(forResource: "ewa_lanczossharp", withExtension: "frag")!
        let ewaSource = try String(contentsOf: ewaURL, encoding: .utf8)
        let ewaLibrary = try device.makeLibrary(source: ewaMetalSource(ewaSource), options: nil)
        descriptor.fragmentFunction = ewaLibrary.makeFunction(name: "ewaFragment")
        ewaPipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        if quality != true {
            let shaderURL = Bundle.main.url(forResource: "Anime4K_Restore_CNN_S", withExtension: "glsl")!
            let shader = try String(contentsOf: shaderURL, encoding: .utf8)
            let computeLibrary = try device.makeLibrary(source: anime4kMetalSource(shader), options: nil)
            computePipelines = try (0..<4).map { try device.makeComputePipelineState(function: computeLibrary.makeFunction(name: "restore\($0)")!) }
        } else { computePipelines = [] }
        qualityPasses = try (quality == false ? [] : ["Anime4K_Restore_CNN_M", "Anime4K_Upscale_CNN_x2_M"]).map { name in
            let source = try String(contentsOf: Bundle.main.url(forResource: name, withExtension: "glsl")!, encoding: .utf8)
            return try metalMediumPasses(source, device: device)
        }
        super.init()
        guard CVMetalTextureCacheCreate(nil, nil, device, nil, &cache) == kCVReturnSuccess else { throw NSError(domain: "Metal texture cache", code: 1) }
    }

    func releaseTextures() {
        generation += 1
        processedTexture = nil
        intermediateTextures.removeAll()
        qualityTextures.removeAll()
        needsRedraw = true
    }

    func clearFrame() {
        frame = nil
        releaseTextures()
        lastFrameTime = 0
        if let cache { CVMetalTextureCacheFlush(cache, 0) }
    }
    func comparePausedFrame() -> String {
        guard let source = frame?.0, let texture = processedTexture,
              let command = queue.makeCommandBuffer(), let blit = command.makeBlitCommandEncoder() else { return "UNVERIFIED same-frame pixel comparison" }
        guard texture.width == CVPixelBufferGetWidth(source), texture.height == CVPixelBufferGetHeight(source) else {
            blit.endEncoding()
            return "UNVERIFIED same-frame comparison requires matching dimensions"
        }
        let rowBytes = ((texture.width * 8 + 255) / 256) * 256
        guard let buffer = device.makeBuffer(length: rowBytes * texture.height, options: .storageModeShared) else { return "FAIL comparison buffer allocation" }
        blit.copy(from: texture, sourceSlice: 0, sourceLevel: 0, sourceOrigin: MTLOrigin(x: 0, y: 0, z: 0), sourceSize: MTLSize(width: texture.width, height: texture.height, depth: 1), to: buffer, destinationOffset: 0, destinationBytesPerRow: rowBytes, destinationBytesPerImage: rowBytes * texture.height)
        blit.endEncoding()
        command.commit()
        command.waitUntilCompleted()
        guard command.status == .completed else { return "FAIL comparison GPU readback" }
        CVPixelBufferLockBaseAddress(source, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(source, .readOnly) }
        guard let original = CVPixelBufferGetBaseAddress(source) else { return "FAIL original buffer access" }
        let stride = CVPixelBufferGetBytesPerRow(source)
        var changed = 0
        var total = 0
        var maxDifference = 0.0
        for y in Swift.stride(from: 0, to: texture.height, by: 4) {
            for x in Swift.stride(from: 0, to: texture.width, by: 4) {
                let raw = original.advanced(by: y * stride + x * 4).assumingMemoryBound(to: UInt8.self)
                let restored = buffer.contents().advanced(by: y * rowBytes + x * 8).assumingMemoryBound(to: UInt16.self)
                var difference = 0.0
                for channel in 0..<3 {
                    let value = min(1, max(0, Double(Float16(bitPattern: restored[channel]))))
                    difference = max(difference, abs(value - Double(raw[2 - channel]) / 255))
                }
                if difference > 1.0 / 255 { changed += 1 }
                maxDifference = max(maxDifference, difference)
                total += 1
            }
        }
        return "\(changed > 0 ? "PASS" : "UNVERIFIED") same-frame changed pixels=\(changed)/\(total), max RGB delta=\(maxDifference); NOT a quality score"
    }
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { needsRedraw = true }
    func draw(in view: MTKView) {
        guard failure == nil, let output else { return }
        let requestedTime = output.itemTime(forHostTime: CACurrentMediaTime() + 1.0 / Double(view.preferredFramesPerSecond))
        var newFrame = false
        if output.hasNewPixelBuffer(forItemTime: requestedTime) {
            var displayTime = CMTime.zero
            if let pixel = output.copyPixelBuffer(forItemTime: requestedTime, itemTimeForDisplay: &displayTime) {
                var reference: CVMetalTexture?
                let status = CVMetalTextureCacheCreateTextureFromImage(nil, cache, pixel, nil, .bgra8Unorm, CVPixelBufferGetWidth(pixel), CVPixelBufferGetHeight(pixel), 0, &reference)
                if status == kCVReturnSuccess, let reference, let texture = CVMetalTextureGetTexture(reference) {
                    frame = (pixel, reference, texture)
                    processedTexture = nil
                    needsRedraw = true
                    lastFrameTime = displayTime.seconds
                    maxTimestampOffset = max(maxTimestampOffset, abs(displayTime.seconds - requestedTime.seconds))
                    newFrame = true
                } else { failure = "texture status \(status)"; return }
            }
        }
        // Polling for a decoded frame is cheap; static frames need no GPU presentation.
        guard frame != nil, newFrame || needsRedraw else { return }
        guard let drawable = view.currentDrawable, let descriptor = view.currentRenderPassDescriptor else { return }
        guard let command = queue.makeCommandBuffer() else { failure = "command buffer unavailable"; return }
        let retainedFrame = frame
        var enhanced = false
        if performanceEnabled && qualityEnabled, let input = retainedFrame?.2, newFrame || processedTexture == nil {
            if qualityTextures.first?.first?.width != input.width || qualityTextures.first?.first?.height != input.height {
                qualityTextures.removeAll()
                processedTexture = nil
                let pixels = input.width * input.height
                let units = qualityPasses.flatMap { $0 }.reduce(0) { $0 + $1.scale * $1.scale }
                let budget: UInt64 = 512 * 1024 * 1024
                guard UInt64(pixels) * UInt64(units) * 8 <= budget else {
                    failure = "Quality texture budget exceeded for \(input.width)x\(input.height)"
                    return
                }
                for passes in qualityPasses {
                    var textures: [MTLTexture] = []
                    for pass in passes {
                        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba16Float, width: input.width * pass.scale, height: input.height * pass.scale, mipmapped: false)
                        descriptor.usage = [.shaderRead, .shaderWrite]
                        descriptor.storageMode = .private
                        guard let texture = device.makeTexture(descriptor: descriptor) else {
                            qualityTextures.removeAll()
                            failure = "Quality texture allocation failed"
                            return
                        }
                        textures.append(texture)
                    }
                    qualityTextures.append(textures)
                }
            }
            var stageInput = input
            for stage in 0..<2 {
                var textures = ["MAIN": stageInput]
                for (index, pass) in qualityPasses[stage].enumerated() {
                    guard let compute = command.makeComputeCommandEncoder() else { failure = "quality encoder unavailable"; return }
                    compute.setComputePipelineState(pass.pipeline)
                    for (binding, name) in pass.bindings.enumerated() { compute.setTexture(textures[name]!, index: binding) }
                    let target = qualityTextures[stage][index]
                    compute.setTexture(target, index: pass.bindings.count)
                    compute.dispatchThreads(MTLSize(width: target.width, height: target.height, depth: 1), threadsPerThreadgroup: MTLSize(width: 8, height: 8, depth: 1))
                    compute.endEncoding()
                    textures[pass.savedName] = target
                }
                stageInput = textures["MAIN"]!
            }
            processedTexture = stageInput
            enhanced = true
        } else if performanceEnabled, let input = retainedFrame?.2, newFrame || processedTexture == nil {
            if intermediateTextures.first?.width != input.width || intermediateTextures.first?.height != input.height {
                let textureDescriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba16Float, width: input.width, height: input.height, mipmapped: false)
                textureDescriptor.usage = [.shaderRead, .shaderWrite]
                textureDescriptor.storageMode = .private
                intermediateTextures = (0..<3).compactMap { _ in device.makeTexture(descriptor: textureDescriptor) }
            }
            guard intermediateTextures.count == 3 else { failure = "FP16 texture allocation failed"; return }
            let sources = [input, intermediateTextures[0], intermediateTextures[1], intermediateTextures[0]]
            let destinations = [intermediateTextures[0], intermediateTextures[1], intermediateTextures[0], intermediateTextures[2]]
            for index in 0..<4 {
                guard let compute = command.makeComputeCommandEncoder() else { failure = "compute encoder unavailable"; return }
                compute.setComputePipelineState(computePipelines[index])
                compute.setTexture(sources[index], index: 0)
                compute.setTexture(input, index: 1)
                compute.setTexture(destinations[index], index: 2)
                compute.dispatchThreads(MTLSize(width: input.width, height: input.height, depth: 1), threadsPerThreadgroup: MTLSize(width: 8, height: 8, depth: 1))
                compute.endEncoding()
            }
            processedTexture = intermediateTextures[2]
            enhanced = true
        }
        guard let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else { failure = "render encoder unavailable"; return }
        if let retainedFrame {
            let texture = performanceEnabled ? processedTexture ?? retainedFrame.2 : retainedFrame.2
            let vertices = videoPresentationVertices(pixelBuffer: retainedFrame.0, targetSize: view.drawableSize, transform: presentationTransform, aspectMode: aspectMode)
            encoder.setRenderPipelineState(performanceEnabled ? ewaPipeline : pipeline)
            vertices.withUnsafeBytes { bytes in
                encoder.setVertexBytes(bytes.baseAddress!, length: bytes.count, index: 0)
            }
            encoder.setFragmentTexture(texture, index: 0)
            encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
        }
        encoder.endEncoding()
        let counter = completedFrames
        let enhancedCounter = completedEnhancedFrames
        let presentationCounter = completedPresentations
        let frameWasEnhanced = enhanced
        let enhancedMode = qualityEnabled ? 2 : 1
        let frameWasNew = newFrame
        let submittedGeneration = generation
        command.addCompletedHandler { buffer in
            withExtendedLifetime(retainedFrame) {
                if buffer.status == .completed { presentationCounter.increment() }
                if frameWasNew && buffer.status == .completed { counter.increment() }
                if frameWasEnhanced && buffer.status == .completed { enhancedCounter.increment() }
                let milliseconds = (buffer.gpuEndTime - buffer.gpuStartTime) * 1000
                Task { @MainActor in
                    guard self.generation == submittedGeneration else { return }
                    self.maxGpuMilliseconds = max(self.maxGpuMilliseconds, milliseconds)
                    if let error = buffer.error { self.failure = error.localizedDescription }
                    if frameWasEnhanced && buffer.status == .completed { self.onEnhancedFrame?(enhancedMode) }
                }
            }
        }
        command.present(drawable)
        command.commit()
        needsRedraw = false
    }
}

/// Positions use square-pixel display dimensions and the track transform; UVs cover the frame's clean aperture.
func videoPresentationVertices(pixelBuffer: CVPixelBuffer, targetSize: CGSize, transform: CGAffineTransform, aspectMode: Int) -> [SIMD4<Float>] {
    let sourceSize = CVImageBufferGetDisplaySize(pixelBuffer)
    let cleanRect = CVImageBufferGetCleanRect(pixelBuffer)
    let width = CGFloat(CVPixelBufferGetWidth(pixelBuffer))
    let height = CGFloat(CVPixelBufferGetHeight(pixelBuffer))
    let bounds = CGRect(origin: .zero, size: sourceSize).applying(transform)
    let sourceAspect = bounds.width / bounds.height
    let targetAspect = targetSize.width / targetSize.height
    var scaleX = min(1, sourceAspect / targetAspect)
    var scaleY = min(1, targetAspect / sourceAspect)
    if aspectMode == 1 { scaleX = 1; scaleY = 1 }
    if aspectMode == 2 { scaleX = max(1, sourceAspect / targetAspect); scaleY = max(1, targetAspect / sourceAspect) }
    let corners: [CGPoint] = [.zero, CGPoint(x: 1, y: 0), CGPoint(x: 0, y: 1), CGPoint(x: 0, y: 1), CGPoint(x: 1, y: 0), CGPoint(x: 1, y: 1)]
    return corners.map { uv in
        let point = CGPoint(x: uv.x * sourceSize.width, y: uv.y * sourceSize.height).applying(transform)
        // CoreVideo's clean rect has a lower-left origin; Metal UVs have an upper-left origin.
        let textureX = (cleanRect.minX + uv.x * cleanRect.width) / width
        let textureY = (height - cleanRect.maxY + uv.y * cleanRect.height) / height
        return SIMD4(Float(((point.x - bounds.minX) / bounds.width * 2 - 1) * scaleX),
                     Float((1 - (point.y - bounds.minY) / bounds.height * 2) * scaleY), Float(textureX), Float(textureY))
    }
}

// The bundled GLSL retains bloc97's MIT license and original CNN weights.
private func anime4kMetalSource(_ glsl: String) -> String {
    let sections = glsl.components(separatedBy: "//!DESC ").dropFirst()
    precondition(sections.count == 4)
    let parameters = (0..<16).map { "float a\($0)" }.joined(separator: ",")
    let columns = (0..<4).map { column in
        "float4(" + (0..<4).map { "a\(column * 4 + $0)" }.joined(separator: ",") + ")"
    }.joined(separator: ",")
    var result = "#include <metal_stdlib>\nusing namespace metal;\ninline float4x4 m4(\(parameters)) { return float4x4(\(columns)); }\n"
    for (index, section) in sections.enumerated() {
        var body = section.components(separatedBy: "\n").dropFirst().filter { !$0.hasPrefix("//!") }.joined(separator: "\n")
        body = body.replacingOccurrences(of: "vec4", with: "float4").replacingOccurrences(of: "vec2", with: "float2").replacingOccurrences(of: "mat4", with: "m4")
        let start = body.range(of: "float4 hook() {")!
        let prefix = String(body[..<start.lowerBound])
        let functionBody = String(body[start.upperBound...])
        let inputName = ["MAIN", "conv2d_tf", "conv2d_1_tf", "conv2d_2_tf"][index]
        result += """
        #define \(inputName)_texOff(offset) image.sample(s, uv + (offset) * texel)
        #define MAIN_tex(position) original.sample(s, position)
        #define MAIN_pos uv
        \(prefix)
        kernel void restore\(index)(texture2d<float, access::sample> image [[texture(0)]], texture2d<float, access::sample> original [[texture(1)]], texture2d<float, access::write> target [[texture(2)]], uint2 gid [[thread_position_in_grid]]) {
            if (gid.x >= target.get_width() || gid.y >= target.get_height()) return;
            constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::nearest);
            float2 texel = 1.0 / float2(target.get_width(), target.get_height());
            float2 uv = (float2(gid) + 0.5) * texel;
        \(functionBody.replacingOccurrences(of: "return result + MAIN_tex(MAIN_pos);", with: "target.write(result + MAIN_tex(MAIN_pos), gid);").replacingOccurrences(of: "return result;", with: "target.write(result, gid);"))
        #undef go_0
        #undef go_1
        #undef \(inputName)_texOff
        #undef MAIN_tex
        #undef MAIN_pos

        """
    }
    return result
}

private struct MetalCnnPass {
    let pipeline: MTLComputePipelineState
    let bindings: [String]
    let savedName: String
    let scale: Int
}

private func metalMediumPasses(_ source: String, device: MTLDevice) throws -> [MetalCnnPass] {
    let sections = source.components(separatedBy: "//!DESC ").dropFirst()
    precondition(sections.count == 8 || sections.count == 9)
    let parameters = (0..<16).map { "float a\($0)" }.joined(separator: ",")
    let columns = (0..<4).map { column in "float4(" + (0..<4).map { "a\(column * 4 + $0)" }.joined(separator: ",") + ")" }.joined(separator: ",")
    var metal = "#include <metal_stdlib>\nusing namespace metal;\ninline float4x4 m4(\(parameters)) { return float4x4(\(columns)); }\n"
    var metadata: [([String], String, Int)] = []
    for (index, section) in sections.enumerated() {
        let lines = section.components(separatedBy: "\n")
        let bindings = lines.filter { $0.hasPrefix("//!BIND ") }.map { String($0.dropFirst(8)) }
        let saved = String(lines.first { $0.hasPrefix("//!SAVE ") }!.dropFirst(8))
        let scale = lines.contains { $0.hasPrefix("//!WIDTH ") && $0.contains("2 *") } ? 2 : 1
        metadata.append((bindings, saved, scale))
        var body = lines.dropFirst().filter { !$0.hasPrefix("//!") }.joined(separator: "\n")
        for (from, to) in [("ivec2", "int2"), ("vec4", "float4"), ("vec2", "float2"), ("mat4", "m4")] { body = body.replacingOccurrences(of: from, with: to) }
        let start = body.range(of: "float4 hook() {")!
        let prefix = String(body[..<start.lowerBound])
        let code = String(body[start.upperBound...]).replacingOccurrences(of: "return ([^;]+);", with: "target.write($1, gid);", options: .regularExpression)
        var arguments: [String] = []
        var definitions: [String] = []
        var names: [String] = []
        for (slot, binding) in bindings.enumerated() {
            arguments.append("texture2d<float, access::sample> t\(slot) [[texture(\(slot))]]")
            definitions += ["#define \(binding)_pos uv", "#define \(binding)_size float2(t\(slot).get_width(), t\(slot).get_height())", "#define \(binding)_pt (1.0 / \(binding)_size)", "#define \(binding)_tex(position) t\(slot).sample(s, position)", "#define \(binding)_texOff(offset) t\(slot).sample(s, uv + (offset) * \(binding)_pt)"]
            names += ["\(binding)_pos", "\(binding)_size", "\(binding)_pt", "\(binding)_tex", "\(binding)_texOff"]
        }
        arguments.append("texture2d<float, access::write> target [[texture(\(bindings.count))]]")
        arguments.append("uint2 gid [[thread_position_in_grid]]")
        names += prefix.components(separatedBy: "\n").filter { $0.hasPrefix("#define ") }.map { String($0.dropFirst(8)).components(separatedBy: CharacterSet(charactersIn: " ("))[0] }
        metal += definitions.joined(separator: "\n") + "\n" + prefix + "\nkernel void cnn\(index)(\(arguments.joined(separator: ","))) {\n"
        metal += "if (gid.x >= target.get_width() || gid.y >= target.get_height()) return;\nconstexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);\nfloat2 uv = (float2(gid) + 0.5) / float2(target.get_width(), target.get_height());\n"
        metal += code + "\n" + names.map { "#undef \($0)" }.joined(separator: "\n") + "\n"
    }
    let library = try device.makeLibrary(source: metal, options: nil)
    return try metadata.enumerated().map { index, data in
        MetalCnnPass(pipeline: try device.makeComputePipelineState(function: library.makeFunction(name: "cnn\(index)")!), bindings: data.0, savedName: data.1, scale: data.2)
    }
}

private func ewaMetalSource(_ glsl: String) -> String {
    var source = glsl.components(separatedBy: "\n").filter { !$0.hasPrefix("#version") && !$0.hasPrefix("precision") && !$0.hasPrefix("varying") && !$0.hasPrefix("uniform") }.joined(separator: "\n")
    for (from, to) in [("vec4", "float4"), ("vec3", "float3"), ("vec2", "float2"), ("const float", "constant float")] { source = source.replacingOccurrences(of: from, with: to) }
    source = source.replacingOccurrences(of: "texture2D(uTexSampler,", with: "image.sample(ewaSampler,")
    source = source.replacingOccurrences(of: "sampleSigmoid(", with: "sampleSigmoid(image, uInputSize, ")
    source = source.replacingOccurrences(of: "float3 sampleSigmoid(image, uInputSize, float2 pixel)", with: "float3 sampleSigmoid(texture2d<float> image, float2 uInputSize, float2 pixel)")
    source = source.replacingOccurrences(of: "void main() {", with: "fragment float4 ewaFragment(V v [[stage_in]], texture2d<float> image [[texture(0)]]) { float2 uInputSize = float2(image.get_width(), image.get_height()); float2 vTexSamplingCoord = v.uv;")
    source = source.replacingOccurrences(of: "gl_FragColor =", with: "return")
    // CNN feature alpha is not video opacity; only the unpainted letterbox is transparent.
    source = source.replacingOccurrences(of: "float4(inverseSigmoid(filtered), alpha)", with: "float4(inverseSigmoid(filtered), 1.0)")
    return "#include <metal_stdlib>\nusing namespace metal;\nstruct V { float4 position [[position]]; float2 uv; };\nconstexpr sampler ewaSampler(coord::normalized, address::clamp_to_edge, filter::linear);\n" + source
}
