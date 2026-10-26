// Package saar is a thin Go client over the Saar command-line interface.
//
// It does not reimplement the codec. It invokes the installed "saar" binary and
// decodes the JSON it prints, which is the same boundary the desktop
// application uses, so one codec implementation stays authoritative.
//
//	client, err := saar.New("")
//	if err != nil { ... }
//	res, err := client.Compress(ctx, "report.pdf", "")
//	if err != nil { ... }
//	_, err = client.Decompress(ctx, res.Output, "restored.pdf")
package saar

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// ErrBinaryNotFound is returned when the Saar CLI cannot be located.
var ErrBinaryNotFound = errors.New("saar: CLI not found; set SAAR_BIN or put 'saar' on PATH")

// CommandError reports a CLI invocation that exited non-zero.
type CommandError struct {
	Command  []string
	ExitCode int
	Stderr   string
}

func (e *CommandError) Error() string {
	detail := strings.TrimSpace(e.Stderr)
	if detail == "" {
		detail = "no diagnostic output"
	}
	return fmt.Sprintf("saar: %s failed with exit %d: %s",
		strings.Join(e.Command, " "), e.ExitCode, detail)
}

// Result is the outcome of one CLI invocation.
type Result struct {
	Command []string
	Output  string
	Payload map[string]any
}

// OK reports the CLI verdict. Commands that carry no verdict return true.
func (r Result) OK() bool {
	v, present := r.Payload["ok"]
	if !present {
		return true
	}
	b, isBool := v.(bool)
	return !isBool || b
}

// Client invokes the Saar CLI.
type Client struct {
	binary string
}

// New returns a client. An empty binary uses SAAR_BIN, then "saar" on PATH.
func New(binary string) (*Client, error) {
	if binary == "" {
		binary = os.Getenv("SAAR_BIN")
	}
	if binary != "" {
		info, err := os.Stat(binary)
		if err != nil || info.IsDir() {
			return nil, fmt.Errorf("saar: SAAR_BIN is not a file: %s", binary)
		}
		return &Client{binary: binary}, nil
	}
	found, err := exec.LookPath("saar")
	if err != nil {
		return nil, ErrBinaryNotFound
	}
	return &Client{binary: found}, nil
}

// Binary returns the absolute path of the CLI in use.
func (c *Client) Binary() string { return c.binary }

func (c *Client) run(ctx context.Context, args ...string) (Result, error) {
	cmd := exec.CommandContext(ctx, c.binary, args...)
	var stdout, stderr strings.Builder
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	res := Result{Command: append([]string{c.binary}, args...), Payload: map[string]any{}}
	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return res, &CommandError{
				Command:  res.Command,
				ExitCode: exitErr.ExitCode(),
				Stderr:   stderr.String(),
			}
		}
		return res, err
	}

	text := strings.TrimSpace(stdout.String())
	if text != "" {
		if err := json.Unmarshal([]byte(text), &res.Payload); err != nil {
			return res, fmt.Errorf("saar: expected JSON from %s: %w",
				strings.Join(res.Command, " "), err)
		}
	}
	if out, ok := res.Payload["output"].(string); ok {
		res.Output = out
	}
	return res, nil
}

// Version returns the CLI version string.
func (c *Client) Version(ctx context.Context) (string, error) {
	cmd := exec.CommandContext(ctx, c.binary, "--version")
	var stdout, stderr strings.Builder
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return "", &CommandError{
				Command:  []string{c.binary, "--version"},
				ExitCode: exitErr.ExitCode(),
				Stderr:   stderr.String(),
			}
		}
		return "", err
	}
	version := strings.TrimSpace(stdout.String())
	if version == "" {
		return "", errors.New("saar: the CLI printed no version")
	}
	return version, nil
}

// Compress compresses one file. An empty output path becomes "<input>.saar".
func (c *Client) Compress(ctx context.Context, input, output string) (Result, error) {
	if output == "" {
		output = input + ".saar"
	}
	return c.run(ctx, "compress", input, "-o", output)
}

// Decompress restores one artifact. An empty output path strips ".saar".
func (c *Client) Decompress(ctx context.Context, input, output string) (Result, error) {
	if output == "" {
		if strings.HasSuffix(input, ".saar") {
			output = strings.TrimSuffix(input, ".saar")
		} else {
			output = input + ".out"
		}
	}
	return c.run(ctx, "decompress", input, "-o", output)
}

// Verify performs the round-trip integrity check.
func (c *Client) Verify(ctx context.Context, input string) (Result, error) {
	return c.run(ctx, "verify", input)
}

// Detect reports the detected modality without compressing.
func (c *Client) Detect(ctx context.Context, input string) (Result, error) {
	return c.run(ctx, "detect", input)
}

// Info describes an existing artifact.
func (c *Client) Info(ctx context.Context, input string) (Result, error) {
	return c.run(ctx, "info", input)
}

// Abs is a small helper for callers that hold relative paths.
func Abs(path string) (string, error) { return filepath.Abs(path) }
