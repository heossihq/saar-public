//! A thin Rust client over the Saar command-line interface.
//!
//! It does not reimplement the codec. It invokes the installed `saar` binary and
//! decodes the JSON it prints, which is the same boundary the desktop
//! application uses, so one codec implementation stays authoritative.

use std::collections::BTreeMap;
use std::fmt;
use std::path::{Path, PathBuf};
use std::process::Command;

/// Failure to locate the CLI.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BinaryNotFound(pub String);

impl fmt::Display for BinaryNotFound {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "saar: {}", self.0)
    }
}

impl std::error::Error for BinaryNotFound {}

/// A CLI invocation that exited non-zero.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommandError {
    pub command: Vec<String>,
    pub exit_code: i32,
    pub stderr: String,
}

impl fmt::Display for CommandError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let detail = if self.stderr.trim().is_empty() {
            "no diagnostic output".to_string()
        } else {
            self.stderr.trim().to_string()
        };
        write!(
            f,
            "saar: {} failed with exit {}: {}",
            self.command.join(" "),
            self.exit_code,
            detail
        )
    }
}

impl std::error::Error for CommandError {}

/// Any SDK failure.
#[derive(Debug)]
pub enum Error {
    BinaryNotFound(BinaryNotFound),
    Command(CommandError),
    Io(std::io::Error),
    Protocol(String),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::BinaryNotFound(e) => write!(f, "{e}"),
            Error::Command(e) => write!(f, "{e}"),
            Error::Io(e) => write!(f, "saar: io error: {e}"),
            Error::Protocol(m) => write!(f, "saar: {m}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(value: std::io::Error) -> Self {
        Error::Io(value)
    }
}

/// Outcome of one CLI invocation.
#[derive(Debug, Clone)]
pub struct Result_ {
    pub command: Vec<String>,
    pub output: Option<PathBuf>,
    pub raw_stdout: String,
    pub values: BTreeMap<String, String>,
}

impl Result_ {
    /// Reports the CLI verdict. Commands without a verdict return true.
    pub fn ok(&self) -> bool {
        match self.values.get("ok") {
            Some(v) => v == "true",
            None => true,
        }
    }
}

/// Invokes the Saar CLI.
#[derive(Debug, Clone)]
pub struct Client {
    binary: PathBuf,
}

impl Client {
    /// Build a client. `None` uses `SAAR_BIN`, then `saar` on PATH.
    pub fn new(binary: Option<&Path>) -> Result<Self, Error> {
        if let Some(path) = binary {
            return Ok(Self {
                binary: path.to_path_buf(),
            });
        }
        if let Ok(env) = std::env::var("SAAR_BIN") {
            if !env.trim().is_empty() {
                let path = PathBuf::from(env);
                if !path.is_file() {
                    return Err(Error::BinaryNotFound(BinaryNotFound(format!(
                        "SAAR_BIN is not a file: {}",
                        path.display()
                    ))));
                }
                return Ok(Self { binary: path });
            }
        }
        which("saar")
            .map(|binary| Self { binary })
            .ok_or_else(|| {
                Error::BinaryNotFound(BinaryNotFound(
                    "CLI not found; set SAAR_BIN or put 'saar' on PATH".to_string(),
                ))
            })
    }

    /// Absolute path of the CLI in use.
    pub fn binary(&self) -> &Path {
        &self.binary
    }

    fn run(&self, args: &[String]) -> Result<Result_, Error> {
        let mut command = vec![self.binary.display().to_string()];
        command.extend(args.iter().cloned());

        let output = Command::new(&self.binary).args(args).output()?;
        if !output.status.success() {
            return Err(Error::Command(CommandError {
                command,
                exit_code: output.status.code().unwrap_or(-1),
                stderr: String::from_utf8_lossy(&output.stderr).to_string(),
            }));
        }

        let raw_stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
        let values = flatten_json(&raw_stdout);
        let output_path = values.get("output").map(PathBuf::from);

        Ok(Result_ {
            command,
            output: output_path,
            raw_stdout,
            values,
        })
    }

    /// CLI version string.
    pub fn version(&self) -> Result<String, Error> {
        let output = Command::new(&self.binary).arg("--version").output()?;
        if !output.status.success() {
            return Err(Error::Command(CommandError {
                command: vec![self.binary.display().to_string(), "--version".into()],
                exit_code: output.status.code().unwrap_or(-1),
                stderr: String::from_utf8_lossy(&output.stderr).to_string(),
            }));
        }
        let version = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if version.is_empty() {
            return Err(Error::Protocol("the CLI printed no version".into()));
        }
        Ok(version)
    }

    /// Compress one file. `None` writes `<input>.saar`.
    pub fn compress(&self, input: &Path, output: Option<&Path>) -> Result<Result_, Error> {
        let target = match output {
            Some(p) => p.to_path_buf(),
            None => {
                let mut s = input.as_os_str().to_os_string();
                s.push(".saar");
                PathBuf::from(s)
            }
        };
        self.run(&[
            "compress".into(),
            input.display().to_string(),
            "-o".into(),
            target.display().to_string(),
        ])
    }

    /// Restore one artifact. `None` strips `.saar`.
    pub fn decompress(&self, input: &Path, output: Option<&Path>) -> Result<Result_, Error> {
        let target = match output {
            Some(p) => p.to_path_buf(),
            None => match input.extension().and_then(|e| e.to_str()) {
                Some("saar") => input.with_extension(""),
                _ => {
                    let mut s = input.as_os_str().to_os_string();
                    s.push(".out");
                    PathBuf::from(s)
                }
            },
        };
        self.run(&[
            "decompress".into(),
            input.display().to_string(),
            "-o".into(),
            target.display().to_string(),
        ])
    }

    /// Round-trip integrity check.
    pub fn verify(&self, input: &Path) -> Result<Result_, Error> {
        self.run(&["verify".into(), input.display().to_string()])
    }

    /// Detected modality, without compressing.
    pub fn detect(&self, input: &Path) -> Result<Result_, Error> {
        self.run(&["detect".into(), input.display().to_string()])
    }

    /// Describe an existing artifact.
    pub fn info(&self, input: &Path) -> Result<Result_, Error> {
        self.run(&["info".into(), input.display().to_string()])
    }
}

fn which(name: &str) -> Option<PathBuf> {
    let path = std::env::var_os("PATH")?;
    std::env::split_paths(&path)
        .map(|dir| dir.join(name))
        .find(|candidate| candidate.is_file())
}

/// Flatten one level of a flat JSON object into key/value strings.
///
/// The CLI emits a flat object of scalars, so a full JSON parser would be
/// unnecessary weight for an SDK that only needs `output` and `ok`.
fn flatten_json(text: &str) -> BTreeMap<String, String> {
    let mut map = BTreeMap::new();
    let trimmed = text.trim();
    if !trimmed.starts_with('{') {
        return map;
    }
    for line in trimmed.trim_matches(|c| c == '{' || c == '}').lines() {
        let line = line.trim().trim_end_matches(',');
        let Some((key, value)) = line.split_once(':') else {
            continue;
        };
        let key = key.trim().trim_matches('"').to_string();
        let value = value.trim().trim_matches('"').to_string();
        if !key.is_empty() {
            map.insert(key, value);
        }
    }
    map
}
