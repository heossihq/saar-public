use saar_sdk::{Client, Error};
use std::fs;
use std::path::PathBuf;

const SAMPLE: &[u8] = b"the quick brown fox jumps over the lazy dog\n";

fn client() -> Client {
    Client::new(None).expect("set SAAR_BIN or put saar on PATH")
}

fn tempdir(tag: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("saar-rs-{tag}-{}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    dir
}

#[test]
fn version_is_reported() {
    let v = client().version().unwrap();
    assert!(v.split('.').count() >= 3, "unexpected version {v}");
}

#[test]
fn round_trip_is_byte_exact() {
    let c = client();
    let dir = tempdir("rt");
    let src = dir.join("sample.txt");
    fs::write(&src, SAMPLE).unwrap();

    let compressed = c.compress(&src, None).unwrap();
    let artifact = compressed.output.expect("no artifact path");
    assert!(artifact.is_file(), "artifact missing");

    let restored = dir.join("restored.txt");
    c.decompress(&artifact, Some(&restored)).unwrap();
    assert_eq!(fs::read(&restored).unwrap(), SAMPLE);
}

#[test]
fn verify_reports_ok() {
    let c = client();
    let dir = tempdir("verify");
    let src = dir.join("v.txt");
    fs::write(&src, SAMPLE).unwrap();
    let artifact = c.compress(&src, None).unwrap().output.unwrap();
    assert!(c.verify(&artifact).unwrap().ok());
}

#[test]
fn missing_input_is_a_command_error() {
    let dir = tempdir("missing");
    match client().compress(&dir.join("absent.txt"), None) {
        Err(Error::Command(e)) => assert_ne!(e.exit_code, 0),
        other => panic!("expected CommandError, got {other:?}"),
    }
}

#[test]
fn corrupt_artifact_is_rejected() {
    let c = client();
    let dir = tempdir("corrupt");
    let bad = dir.join("corrupt.saar");
    fs::write(&bad, b"not a saar artifact").unwrap();
    assert!(c.decompress(&bad, Some(&dir.join("out.bin"))).is_err());
}

#[test]
fn refuses_to_overwrite_existing_output() {
    let c = client();
    let dir = tempdir("overwrite");
    let src = dir.join("a.txt");
    fs::write(&src, SAMPLE).unwrap();
    let artifact = c.compress(&src, None).unwrap().output.unwrap();

    let occupied = dir.join("occupied.txt");
    fs::write(&occupied, b"keep me").unwrap();
    assert!(c.decompress(&artifact, Some(&occupied)).is_err());
    assert_eq!(fs::read(&occupied).unwrap(), b"keep me");
}
