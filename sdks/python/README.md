# saar-sdk

Python client for Saar, a byte-exact lossless compressor.

This package is a thin wrapper over the Saar command-line interface. It does
not reimplement the codec: it invokes the installed `saar` binary and decodes
the JSON it prints, so one implementation stays authoritative and the SDK
behaves the same as the desktop application.

## Install

```bash
pip install saar-sdk
```

The Saar CLI must be installed separately and reachable as `saar` on `PATH`, or
via `SAAR_BIN`.

## Use

```python
from saar_sdk import SaarClient

client = SaarClient()                  # SAAR_BIN, else saar on PATH
result = client.compress("report.pdf") # -> report.pdf.saar

assert client.verify(result.output).ok
client.decompress(result.output, "restored.pdf")
```

## Contract

| Operation | Behaviour |
|---|---|
| `version()` | CLI version string |
| `compress(input, output=None)` | Default output is `<input>.saar` |
| `decompress(input, output=None)` | Default strips `.saar` |
| `verify(input)` | Round-trip integrity check; `result.ok` |
| `detect(input)` | Detected modality, without compressing |
| `info(input)` | Describe an existing artifact |

Failures raise `SaarCommandError`, which carries the argv, the exit code and
stderr. An existing output file is never overwritten: the CLI refuses and the
SDK propagates that refusal.

## Licence

Apache-2.0 for this client library. The Saar engine, CLI and file formats
remain proprietary and are licensed separately. See `NOTICE`.
