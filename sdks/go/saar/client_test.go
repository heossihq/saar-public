package saar

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"testing"
)

var sample = []byte("the quick brown fox jumps over the lazy dog\n")

func newClient(t *testing.T) *Client {
	t.Helper()
	c, err := New("")
	if err != nil {
		t.Fatalf("New: %v (set SAAR_BIN or put saar on PATH)", err)
	}
	return c
}

func writeSample(t *testing.T, dir string) string {
	t.Helper()
	p := filepath.Join(dir, "sample.txt")
	if err := os.WriteFile(p, sample, 0o600); err != nil {
		t.Fatalf("write sample: %v", err)
	}
	return p
}

func TestVersion(t *testing.T) {
	v, err := newClient(t).Version(context.Background())
	if err != nil {
		t.Fatalf("Version: %v", err)
	}
	if !regexp.MustCompile(`^\d+\.\d+\.\d+`).MatchString(v) {
		t.Fatalf("unexpected version %q", v)
	}
}

func TestRoundTripIsByteExact(t *testing.T) {
	c, ctx := newClient(t), context.Background()
	dir := t.TempDir()
	src := writeSample(t, dir)

	compressed, err := c.Compress(ctx, src, "")
	if err != nil {
		t.Fatalf("Compress: %v", err)
	}
	if compressed.Output == "" {
		t.Fatal("no artifact path returned")
	}
	if _, err := os.Stat(compressed.Output); err != nil {
		t.Fatalf("artifact missing: %v", err)
	}

	restored := filepath.Join(dir, "restored.txt")
	if _, err := c.Decompress(ctx, compressed.Output, restored); err != nil {
		t.Fatalf("Decompress: %v", err)
	}
	got, err := os.ReadFile(restored)
	if err != nil {
		t.Fatalf("read restored: %v", err)
	}
	if string(got) != string(sample) {
		t.Fatal("round trip is not byte exact")
	}
}

func TestVerifyReportsOK(t *testing.T) {
	c, ctx := newClient(t), context.Background()
	dir := t.TempDir()
	compressed, err := c.Compress(ctx, writeSample(t, dir), "")
	if err != nil {
		t.Fatalf("Compress: %v", err)
	}
	res, err := c.Verify(ctx, compressed.Output)
	if err != nil {
		t.Fatalf("Verify: %v", err)
	}
	if !res.OK() {
		t.Fatalf("verify reported not ok: %v", res.Payload)
	}
}

func TestMissingInputReturnsCommandError(t *testing.T) {
	c, ctx := newClient(t), context.Background()
	_, err := c.Compress(ctx, filepath.Join(t.TempDir(), "absent.txt"), "")
	var cmdErr *CommandError
	if !errors.As(err, &cmdErr) {
		t.Fatalf("expected CommandError, got %v", err)
	}
	if cmdErr.ExitCode == 0 {
		t.Fatal("expected a non-zero exit code")
	}
}

func TestCorruptArtifactRejected(t *testing.T) {
	c, ctx := newClient(t), context.Background()
	dir := t.TempDir()
	bad := filepath.Join(dir, "corrupt.saar")
	if err := os.WriteFile(bad, []byte("not a saar artifact"), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}
	if _, err := c.Decompress(ctx, bad, filepath.Join(dir, "out.bin")); err == nil {
		t.Fatal("corrupt artifact was accepted")
	}
}

func TestRefusesToOverwrite(t *testing.T) {
	c, ctx := newClient(t), context.Background()
	dir := t.TempDir()
	compressed, err := c.Compress(ctx, writeSample(t, dir), "")
	if err != nil {
		t.Fatalf("Compress: %v", err)
	}
	occupied := filepath.Join(dir, "occupied.txt")
	if err := os.WriteFile(occupied, []byte("keep me"), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}
	if _, err := c.Decompress(ctx, compressed.Output, occupied); err == nil {
		t.Fatal("existing output was overwritten")
	}
	got, _ := os.ReadFile(occupied)
	if string(got) != "keep me" {
		t.Fatal("existing output was modified")
	}
}
