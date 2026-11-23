import CoreVideo
import Darwin
import XCTest

final class VideoEnhancementGeometryTests: XCTestCase {
    private func makeFrame(width: Int, height: Int, aperture: [CFString: Int]? = nil, pixelAspect: [CFString: Int]? = nil) -> CVPixelBuffer {
        var buffer: CVPixelBuffer?
        let result = CVPixelBufferCreate(nil, width, height, kCVPixelFormatType_32BGRA, nil, &buffer)
        XCTAssertEqual(result, kCVReturnSuccess)
        let frame = buffer!
        if let aperture { CVBufferSetAttachment(frame, kCVImageBufferCleanApertureKey, aperture as CFDictionary, .shouldPropagate) }
        if let pixelAspect { CVBufferSetAttachment(frame, kCVImageBufferPixelAspectRatioKey, pixelAspect as CFDictionary, .shouldPropagate) }
        return frame
    }

    private func offsetFrame() -> CVPixelBuffer {
        makeFrame(width: 128, height: 96, aperture: [
            kCVImageBufferCleanApertureWidthKey: 100,
            kCVImageBufferCleanApertureHeightKey: 60,
            kCVImageBufferCleanApertureHorizontalOffsetKey: -4,
            kCVImageBufferCleanApertureVerticalOffsetKey: 13,
        ])
    }

    private func assertVertex(_ actual: SIMD4<Float>, _ expected: SIMD4<Float>, file: StaticString = #filePath, line: UInt = #line) {
        for component in 0..<4 {
            XCTAssertEqual(actual[component], expected[component], accuracy: 0.00001, file: file, line: line)
        }
    }

    @objc func testFullFrameWithoutAttachments() {
        let vertices = videoPresentationVertices(pixelBuffer: makeFrame(width: 320, height: 180), targetSize: CGSize(width: 320, height: 180), transform: .identity, aspectMode: 0)
        assertVertex(vertices[0], SIMD4(-1, 1, 0, 0))
        assertVertex(vertices[5], SIMD4(1, -1, 1, 1))
    }

    @objc func testPaddedHeightUsesVisibleAspectAndCropsTopRows() {
        let frame = makeFrame(width: 1920, height: 1088, aperture: [
            kCVImageBufferCleanApertureWidthKey: 1920,
            kCVImageBufferCleanApertureHeightKey: 1080,
            kCVImageBufferCleanApertureHorizontalOffsetKey: 0,
            kCVImageBufferCleanApertureVerticalOffsetKey: 4,
        ])
        let vertices = videoPresentationVertices(pixelBuffer: frame, targetSize: CGSize(width: 1920, height: 1080), transform: .identity, aspectMode: 0)
        assertVertex(vertices[0], SIMD4(-1, 1, 0, 8.0 / 1088))
        assertVertex(vertices[5], SIMD4(1, -1, 1, 1))
    }

    @objc func testOffsetApertureConvertsLowerLeftOrigin() {
        let vertices = videoPresentationVertices(pixelBuffer: offsetFrame(), targetSize: CGSize(width: 100, height: 60), transform: .identity, aspectMode: 0)
        assertVertex(vertices[0], SIMD4(-1, 1, 10.0 / 128, 31.0 / 96))
        assertVertex(vertices[5], SIMD4(1, -1, 110.0 / 128, 91.0 / 96))
    }

    @objc func testNonSquarePixelsUseDisplayAspect() {
        let frame = makeFrame(width: 720, height: 480, aperture: [
            kCVImageBufferCleanApertureWidthKey: 704,
            kCVImageBufferCleanApertureHeightKey: 480,
            kCVImageBufferCleanApertureHorizontalOffsetKey: 0,
            kCVImageBufferCleanApertureVerticalOffsetKey: 0,
        ], pixelAspect: [
            kCVImageBufferPixelAspectRatioHorizontalSpacingKey: 10,
            kCVImageBufferPixelAspectRatioVerticalSpacingKey: 11,
        ])
        let vertices = videoPresentationVertices(pixelBuffer: frame, targetSize: CGSize(width: 640, height: 480), transform: .identity, aspectMode: 0)
        assertVertex(vertices[0], SIMD4(-1, 1, 8.0 / 720, 0))
        assertVertex(vertices[5], SIMD4(1, -1, 712.0 / 720, 1))
    }

    @objc func testRotationsAndMirroringPreserveCroppedUVs() {
        let cases: [(CGAffineTransform, CGSize, SIMD2<Float>)] = [
            (CGAffineTransform(a: 0, b: 1, c: -1, d: 0, tx: 96, ty: 0), CGSize(width: 60, height: 100), SIMD2(1, 1)),
            (CGAffineTransform(a: -1, b: 0, c: 0, d: -1, tx: 128, ty: 96), CGSize(width: 100, height: 60), SIMD2(1, -1)),
            (CGAffineTransform(a: 0, b: -1, c: 1, d: 0, tx: 0, ty: 128), CGSize(width: 60, height: 100), SIMD2(-1, -1)),
            (CGAffineTransform(a: -1, b: 0, c: 0, d: 1, tx: 128, ty: 0), CGSize(width: 100, height: 60), SIMD2(1, 1)),
        ]
        for (transform, target, position) in cases {
            let vertices = videoPresentationVertices(pixelBuffer: offsetFrame(), targetSize: target, transform: transform, aspectMode: 0)
            assertVertex(vertices[0], SIMD4(position.x, position.y, 10.0 / 128, 31.0 / 96))
            assertVertex(vertices[5], SIMD4(-position.x, -position.y, 110.0 / 128, 91.0 / 96))
        }
    }

    @objc func testFitStretchAndCropUseCleanDisplayBounds() {
        let scales: [SIMD2<Float>] = [SIMD2(1, 0.6), SIMD2(1, 1), SIMD2(100.0 / 60, 1)]
        for (mode, scale) in scales.enumerated() {
            let vertices = videoPresentationVertices(pixelBuffer: offsetFrame(), targetSize: CGSize(width: 100, height: 100), transform: .identity, aspectMode: mode)
            assertVertex(vertices[0], SIMD4(-scale.x, scale.y, 10.0 / 128, 31.0 / 96))
            assertVertex(vertices[5], SIMD4(scale.x, -scale.y, 110.0 / 128, 91.0 / 96))
        }
    }
}

@main
enum VideoEnhancementGeometryTestRunner {
    static func main() {
        let suite = VideoEnhancementGeometryTests.defaultTestSuite
        suite.run()
        exit(suite.testRun?.hasSucceeded == true && suite.testCaseCount > 0 ? 0 : 1)
    }
}
