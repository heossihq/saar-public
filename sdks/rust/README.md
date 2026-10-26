# saar-sdk

Rust client for Saar, a byte-exact lossless compressor.

A thin wrapper over the Saar command-line interface. It does not reimplement
the codec: it invokes the installed `saar` binary and reads the JSON it prints,
so one implementation stays authoritative.

```rust
use saar_sdk::Client;

let client = Client::new(None)?;              // SAAR_BIN, else saar on PATH
let compressed = client.compress("report.pdf".as_ref(), None)?;
client.decompress(&compressed.output.unwrap(), Some("restored.pdf".as_ref()))?;
# Ok::<(), saar_sdk::Error>(())
```

`Client::new(None)` resolves `SAAR_BIN` first, then `saar` on `PATH`. Failures
surface as `Error::Command` carrying argv, exit code and stderr. An existing
output file is never overwritten.

## Licence

Apache-2.0 for this client library. The Saar engine, CLI and file formats
remain proprietary and are licensed separately. See `NOTICE`.
