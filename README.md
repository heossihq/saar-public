# Saar

**Content-aware lossless compression, delivered as an embeddable engine.**

Saar is a proprietary lossless compression engine from [HEOSSI](https://heossi.com). It is not a general-purpose archive format and it is not open source: the codec is licensed, the integration surfaces are documented here.

This repository is the public integration and documentation surface — the MCP server contract, client configuration, and usage guidance. It does **not** contain the compression implementation.

[Website](https://saar.heossi.com) | [Customer portal](https://portal.saar.heossi.com) | [Licensing](#licensing) | [MCP server](#mcp-server)

## What Saar is

- **Content-aware lossless compression** — the encoder selects a mode per input rather than applying one fixed algorithm.
- **Embeddable** — a TypeScript library and file CLI, plus an independently implemented C11 SDK and stdin/stdout CLI, with interoperability tests between them.
- **Licensed, not open source** — commercial use runs on signed licence tokens issued through the portal, verified by the TypeScript and native verifiers. A signed revocation list is enforced locally.
- **Byte-exact round trips** — every encoder mode decodes to the original bytes; interoperability is exercised in both directions between the TypeScript and native implementations.

The public workload-class matrix and dated measurements live with the product documentation, not in this repository. Where performance is quoted, it is quoted with its measurement date and workload.

## MCP server

`@heossihq/saar-mcp` exposes Saar through the Model Context Protocol. It is a thin server: it does **not** contain the compressor, it drives an installed Saar native CLI.

| Tool | Purpose |
| --- | --- |
| `saar_compress_file` | Compress a file through the installed native CLI |
| `saar_decompress_file` | Restore a compressed file |
| `saar_version` | Report the engine and CLI versions in use |
| `saar_license_status` | Report the current licence state |

Requirements: **Node.js 20+** and Saar `saar_native` **1.0.0 or later**.

Configuration:

| Variable | Meaning |
| --- | --- |
| `SAAR_NATIVE_BINARY` | Path to the `saar_native` executable (required) |
| `SAAR_ALLOWED_ROOTS` | OS-path-delimited list of directories the file tools may touch; defaults to the current directory |
| `SAAR_LICENSE_KEY` | Signed licence token, or use `~/.saar/license.json` created by `saar license activate` |
| `SAAR_REQUIRE_LICENSE` | Set to `1` to refuse file operations without a valid licence bearing the `mcp` feature |

Without a licence the server runs in evaluation mode and reports that state through `saar_license_status` rather than pretending to be licensed.

Client configuration templates are in [`mcp/configs`](./mcp/configs).

## Licensing

Five NIST-tiered local licences — Personal, Developer, Business, Enterprise and Government — are issued through the [portal](https://portal.saar.heossi.com) as QNSI-signed tokens. SDK and OEM licensing is arranged separately.

The engine is proprietary. Nothing in this repository grants a licence to the codec, and the absence of source here is intentional rather than incomplete.

## Security

Report vulnerabilities privately as described in [SECURITY.md](./SECURITY.md). Do not open a public issue for a suspected vulnerability.

## Trademarks

Saar and HEOSSI are trademarks of HEOSSI (Pte.) Ltd.
