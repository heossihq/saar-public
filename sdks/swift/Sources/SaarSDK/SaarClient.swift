import Foundation

/// Outcome of one CLI invocation.
public struct SaarResult: Sendable {
    /// The argv that ran.
    public let command: [String]
    /// The artifact or restored path the CLI reported, if any.
    public let output: URL?
    /// Raw standard output.
    public let stdout: String
    /// Flat key/value view of the JSON object.
    public let values: [String: String]

    /// Reports the CLI verdict; commands without a verdict return true.
    public var ok: Bool {
        guard let verdict = values["ok"] else { return true }
        return verdict == "true" || verdict == "1"
    }
}

/// A thin client over the Saar command-line interface.
///
/// It does not reimplement the codec. It invokes the installed `saar` binary
/// and decodes the JSON it prints, which is the same boundary the desktop
/// application uses, so one codec implementation stays authoritative.
public struct SaarClient: Sendable {
    /// Absolute path of the CLI in use.
    public let binary: URL
    private let timeout: TimeInterval

    /// Create a client using `SAAR_BIN`, then `saar` on PATH.
    public static func make(timeout: TimeInterval = 3600) throws -> SaarClient {
        if let env = ProcessInfo.processInfo.environment["SAAR_BIN"],
           !env.trimmingCharacters(in: .whitespaces).isEmpty {
            let url = URL(fileURLWithPath: env)
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw SaarError.binaryNotFound("SAAR_BIN is not a file: \(url.path)")
            }
            return SaarClient(binary: url, timeout: timeout)
        }

        let path = ProcessInfo.processInfo.environment["PATH"] ?? ""
        for directory in path.split(separator: ":") {
            let candidate = URL(fileURLWithPath: String(directory)).appendingPathComponent("saar")
            if FileManager.default.fileExists(atPath: candidate.path) {
                return SaarClient(binary: candidate, timeout: timeout)
            }
        }
        throw SaarError.binaryNotFound("CLI not found; set SAAR_BIN or put 'saar' on PATH")
    }

    /// CLI version string.
    public func version() throws -> String {
        let result = try run(["--version"])
        let text = result.stdout.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            throw SaarError.protocolViolation("the CLI printed no version")
        }
        return text
    }

    /// Compress one file. Passing `nil` writes `<input>.saar`.
    @discardableResult
    public func compress(_ input: URL, to output: URL? = nil) throws -> SaarResult {
        let target = output ?? URL(fileURLWithPath: input.path + ".saar")
        return try run(["compress", input.path, "-o", target.path])
    }

    /// Restore one artifact. Passing `nil` strips `.saar`.
    @discardableResult
    public func decompress(_ input: URL, to output: URL? = nil) throws -> SaarResult {
        let target = output ?? URL(fileURLWithPath: Self.defaultRestorePath(input))
        return try run(["decompress", input.path, "-o", target.path])
    }

    /// Round-trip integrity check.
    public func verify(_ input: URL) throws -> SaarResult {
        try run(["verify", input.path])
    }

    /// Detected modality, without compressing.
    public func detect(_ input: URL) throws -> SaarResult {
        try run(["detect", input.path])
    }

    /// Describe an existing artifact.
    public func info(_ input: URL) throws -> SaarResult {
        try run(["info", input.path])
    }

    private static func defaultRestorePath(_ input: URL) -> String {
        input.path.hasSuffix(".saar")
            ? String(input.path.dropLast(".saar".count))
            : input.path + ".out"
    }

    private func run(_ args: [String]) throws -> SaarResult {
        let command = [binary.path] + args
        let process = Process()
        process.executableURL = binary
        process.arguments = args

        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()
        process.standardOutput = stdoutPipe
        process.standardError = stderrPipe

        do {
            try process.run()
        } catch {
            throw SaarError.binaryNotFound("could not start \(binary.path): \(error)")
        }

        let deadline = Date().addingTimeInterval(timeout)
        while process.isRunning && Date() < deadline {
            usleep(20_000)
        }
        if process.isRunning {
            process.terminate()
            throw SaarError.protocolViolation("timed out after \(Int(timeout))s: \(command.joined(separator: " "))")
        }

        let outData = stdoutPipe.fileHandleForReading.readDataToEndOfFile()
        let errData = stderrPipe.fileHandleForReading.readDataToEndOfFile()
        let stdout = String(decoding: outData, as: UTF8.self)
        let stderr = String(decoding: errData, as: UTF8.self)

        guard process.terminationStatus == 0 else {
            throw SaarError.commandFailed(
                command: command,
                exitCode: process.terminationStatus,
                stderr: stderr
            )
        }

        let values = Self.flatten(stdout)
        let output = values["output"].map { URL(fileURLWithPath: $0) }
        return SaarResult(command: command, output: output, stdout: stdout, values: values)
    }

    /// Flatten one level of the flat JSON object the CLI prints.
    private static func flatten(_ stdout: String) -> [String: String] {
        let text = stdout.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.hasPrefix("{") else { return [:] }
        var result: [String: String] = [:]
        for line in text.dropFirst().dropLast().split(separator: "\n") {
            var trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasSuffix(",") { trimmed = String(trimmed.dropLast()) }
            guard let colon = trimmed.firstIndex(of: ":") else { continue }
            let key = trimmed[..<colon].trimmingCharacters(in: CharacterSet(charactersIn: " \""))
            let value = trimmed[trimmed.index(after: colon)...]
                .trimmingCharacters(in: CharacterSet(charactersIn: " \""))
            if !key.isEmpty { result[key] = value }
        }
        return result
    }
}
