# Saar for Visual Studio Code

The editor surface for Saar. This page documents the extension; the extension
itself is distributed through the Visual Studio Marketplace, and its source is
not published here.

[Install from the Marketplace](https://marketplace.visualstudio.com/items?itemName=heossi.saar)

## What it does

The extension is the editor window onto the installed Saar command line. It
drives the same engine, the same recovery check and the same licence
verification as the CLI. It does not contain or reimplement the codec.

| Guarantee | Behaviour |
| --- | --- |
| Verified recovery | A candidate is emitted only after the decoder reproduces the source bytes. |
| Bounded expansion | When nothing compresses the bytes are stored, so worst case is container overhead. |
| Nothing overwritten | Output is written beside the input; an existing file is never replaced. |

## Requirements

| Requirement | Detail |
| --- | --- |
| Saar CLI | Version 1.0.0 or later, available as `saar_native` on `PATH`. |
| Override | Set `saar.executablePath` if the binary lives elsewhere. |
| Licence | A valid Saar licence is required to use the engine. |

## Commands

| Command | Purpose |
| --- | --- |
| `saar.compress` | Compress the active file, writing the artifact beside it. |
| `saar.decompress` | Restore an artifact, with the recovery check. |
| `saar.activateLicense` | Store an activation code in SecretStorage. |
| `saar.licenseStatus` | Report tier, algorithm and level. |
| `saar.deactivateLicense` | Remove the stored licence. |

## Settings

| Setting | Default |
| --- | --- |
| `saar.executablePath` | `saar_native` |
| `saar.profile` | `full` |
| `saar.maxOutputBytes` | `268435456` |
| `saar.licenseServer` | `portal.saar.heossi.com` |
| `saar.licensePublicKey` | empty |
| `saar.licensePqcPublicKey` | empty |
| `saar.requireLicense` | `false` |
| `saar.requirePqc` | `false` |
| `saar.requireFullPqc` | `false` |

## Licence

The extension is a proprietary HEOSSI product and its source is not published.
See [LICENSE.md](../../LICENSE.md).
