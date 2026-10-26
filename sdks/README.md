# Saar SDKs

Thin, tested clients over the Saar command-line interface, one per language.

Every SDK follows the same contract and the same boundary: it invokes the
installed `saar` binary and decodes the JSON it prints. None of them
reimplements the codec. That keeps a single authoritative implementation and
means every SDK behaves identically, including its failure modes.

## Shared contract

| Operation                    | Behaviour                                             |
| ---------------------------- | ----------------------------------------------------- |
| `version()`                  | Returns the CLI version string                        |
| `compress(input, output?)`   | Compresses one file; default output is `<input>.saar` |
| `decompress(input, output?)` | Restores one artifact; default strips `.saar`         |
| `verify(input)`              | Round-trip integrity check; reports a verdict         |
| `detect(input)`              | Detected modality, without compressing                |
| `info(input)`                | Describes an existing artifact                        |

Binary resolution in every SDK: `SAAR_BIN` if set, otherwise `saar` on `PATH`.
Failures surface as a typed command error carrying the argv, exit code and
stderr. No SDK overwrites an existing output file; the CLI refuses and the SDK
propagates that refusal.

## Conformance

Each SDK ships a test suite that runs the real CLI and asserts:
1. version is reported,
2. a round trip is byte-exact,
3. `verify` reports success,
4. a missing input raises a command error,
5. a corrupt artifact is rejected,
6. an existing output file is never overwritten.

Run one suite with `SAAR_BIN` set to the CLI:

| Language | Command                                                              |
| -------- | -------------------------------------------------------------------- |
| Python   | `cd sdks/python && python3 -m unittest discover -s tests -v`         |
| Go       | `cd sdks/go && go test ./...`                                        |
| Rust     | `cd sdks/rust && cargo test`                                         |
| JVM      | `cd sdks/jvm && gradle test`                                         |
| .NET     | `cd sdks/dotnet && dotnet test Saar.Sdk.Tests/Saar.Sdk.Tests.csproj` |
| Swift    | `cd sdks/swift && swift test`                                        |

All six suites were run against the same CLI build and pass.

## Package identities

| Language | Coordinate                                |
| -------- | ----------------------------------------- |
| Python   | `saar-sdk` (PyPI), import `saar_sdk`      |
| Go       | `github.com/heossihq/saar-public/sdks/go` |
| Rust     | `saar-sdk` (crates.io)                    |
| JVM      | `com.heossi:saar-sdk` (Maven Central)     |
| .NET     | `Heossi.Saar` (NuGet)                     |
| Swift    | `SaarSDK` (Swift Package Manager)         |

These follow the same vendor scopes used by the QNSI SDKs (`com.heossi`,
`@heossihq`, `heossihq/*-public`), so the HEOSSI family stays consistent across
registries.

## What these SDKs do not do

- They do not bundle or ship the codec. The CLI must be installed separately.
- They do not implement streaming or bundle operations yet; only the six
  operations above are covered by tests.
- They make no performance claim. Timing depends on the host and the profile.
