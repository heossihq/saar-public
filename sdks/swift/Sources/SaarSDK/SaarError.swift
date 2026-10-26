import Foundation

/// Any Saar SDK failure.
public enum SaarError: Error, CustomStringConvertible {
    /// The Saar CLI could not be located.
    case binaryNotFound(String)
    /// The CLI ran but exited non-zero.
    case commandFailed(command: [String], exitCode: Int32, stderr: String)
    /// The CLI produced output the SDK could not interpret.
    case protocolViolation(String)

    public var description: String {
        switch self {
        case .binaryNotFound(let message):
            return "saar: \(message)"
        case .commandFailed(let command, let exitCode, let stderr):
            let detail = stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            return "saar: \(command.joined(separator: " ")) failed with exit \(exitCode): "
                + (detail.isEmpty ? "no diagnostic output" : detail)
        case .protocolViolation(let message):
            return "saar: \(message)"
        }
    }
}
