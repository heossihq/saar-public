import Foundation
import XCTest

@testable import SaarSDK

final class SaarClientTests: XCTestCase {
    private static let sample = Data("the quick brown fox jumps over the lazy dog\n".utf8)

    private var directory: URL!
    private var client: SaarClient!

    override func setUpWithError() throws {
        client = try SaarClient.make()
        directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("saar-swift-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    private func writeSample(_ name: String = "sample.txt") throws -> URL {
        let url = directory.appendingPathComponent(name)
        try Self.sample.write(to: url)
        return url
    }

    func testVersionIsReported() throws {
        let version = try client.version()
        XCTAssertNotNil(version.range(of: #"^\d+\.\d+\.\d+"#, options: .regularExpression))
    }

    func testRoundTripIsByteExact() throws {
        let source = try writeSample()
        let compressed = try client.compress(source)
        let artifact = try XCTUnwrap(compressed.output)
        XCTAssertTrue(FileManager.default.fileExists(atPath: artifact.path))

        let restored = directory.appendingPathComponent("restored.txt")
        try client.decompress(artifact, to: restored)
        XCTAssertEqual(try Data(contentsOf: restored), Self.sample)
    }

    func testVerifyReportsOk() throws {
        let artifact = try XCTUnwrap(try client.compress(try writeSample()).output)
        XCTAssertTrue(try client.verify(artifact).ok)
    }

    func testMissingInputFails() throws {
        XCTAssertThrowsError(try client.compress(directory.appendingPathComponent("absent.txt"))) { error in
            guard case SaarError.commandFailed(_, let code, _) = error else {
                return XCTFail("expected commandFailed, got \(error)")
            }
            XCTAssertNotEqual(code, 0)
        }
    }

    func testCorruptArtifactRejected() throws {
        let bad = directory.appendingPathComponent("corrupt.saar")
        try Data("not a saar artifact".utf8).write(to: bad)
        XCTAssertThrowsError(try client.decompress(bad, to: directory.appendingPathComponent("out.bin")))
    }

    func testRefusesToOverwrite() throws {
        let artifact = try XCTUnwrap(try client.compress(try writeSample("a.txt")).output)
        let occupied = directory.appendingPathComponent("occupied.txt")
        try Data("keep me".utf8).write(to: occupied)
        XCTAssertThrowsError(try client.decompress(artifact, to: occupied))
        XCTAssertEqual(try String(contentsOf: occupied, encoding: .utf8), "keep me")
    }
}
