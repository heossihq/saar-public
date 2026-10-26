"""A thin, honest Python client over the Saar command-line interface.

This SDK does not reimplement the codec. It invokes the installed ``saar``
binary and interprets the JSON it prints, which is the same boundary the
desktop application uses. That keeps one codec implementation authoritative
and makes the SDK usable on any platform where the CLI runs.

Typical use::

    from saar_sdk import SaarClient

    client = SaarClient()                 # uses SAAR_BIN or ``saar`` on PATH
    result = client.compress("report.pdf")
    assert client.verify(result.output).ok
    client.decompress(result.output, "restored.pdf")
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .errors import SaarBinaryNotFound, SaarCommandError, SaarProtocolError

__all__ = ["SaarClient", "SaarResult"]

DEFAULT_TIMEOUT_SECONDS = 3600


@dataclass(frozen=True)
class SaarResult:
    """Outcome of one CLI invocation."""

    command: list[str]
    output: str | None
    payload: dict[str, Any] = field(default_factory=dict)

    @property
    def ok(self) -> bool:
        """True when the CLI reported success explicitly, or reported no verdict."""
        value = self.payload.get("ok")
        return True if value is None else bool(value)

    def __getitem__(self, key: str) -> Any:
        return self.payload[key]


def _resolve_binary(binary: str | os.PathLike[str] | None) -> str:
    candidate = str(binary) if binary is not None else os.environ.get("SAAR_BIN")
    if candidate:
        path = Path(candidate).expanduser()
        if not path.is_file():
            raise SaarBinaryNotFound(f"SAAR_BIN points to a missing file: {path}")
        return str(path)
    found = shutil.which("saar")
    if not found:
        raise SaarBinaryNotFound(
            "Could not find the Saar CLI. Install it so that 'saar' is on PATH, "
            "or set SAAR_BIN to its absolute path."
        )
    return found


class SaarClient:
    """Invoke the Saar CLI with predictable, checked behaviour."""

    def __init__(
        self,
        binary: str | os.PathLike[str] | None = None,
        timeout: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> None:
        self._binary = _resolve_binary(binary)
        self.timeout = timeout

    @property
    def binary(self) -> str:
        """Absolute path of the CLI in use."""
        return self._binary

    # ------------------------------------------------------------------ core

    def _run(self, args: list[str]) -> SaarResult:
        command = [self._binary, *args]
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=self.timeout,
                check=False,
            )
        except FileNotFoundError as exc:  # pragma: no cover - defensive
            raise SaarBinaryNotFound(str(exc)) from exc
        if completed.returncode != 0:
            raise SaarCommandError(command, completed.returncode, completed.stderr)

        payload: dict[str, Any] = {}
        text = completed.stdout.strip()
        if text:
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError as exc:
                raise SaarProtocolError(
                    f"expected JSON from {' '.join(command)}, got: {text[:200]!r}"
                ) from exc
            if isinstance(parsed, dict):
                payload = parsed
            else:  # lists are valid for some commands
                payload = {"value": parsed}

        output = payload.get("output")
        return SaarResult(
            command=command,
            output=str(output) if isinstance(output, str) else None,
            payload=payload,
        )

    # -------------------------------------------------------------- operations

    def version(self) -> str:
        """Return the CLI version string."""
        command = [self._binary, "--version"]
        completed = subprocess.run(
            command, capture_output=True, text=True, timeout=60, check=False
        )
        if completed.returncode != 0:
            raise SaarCommandError(command, completed.returncode, completed.stderr)
        text = completed.stdout.strip()
        if not text:
            raise SaarProtocolError("the CLI printed no version")
        return text

    def compress(
        self,
        input_path: str | os.PathLike[str],
        output_path: str | os.PathLike[str] | None = None,
        *,
        profile: str | None = None,
        modality: str | None = None,
    ) -> SaarResult:
        """Compress one file. Returns the CLI result; ``result.output`` is the artifact path.

        The CLI never overwrites an existing output file, and neither does this
        method: it passes the resolved path through and lets the CLI refuse.
        """
        source = Path(input_path)
        target = Path(output_path) if output_path is not None else source.with_suffix(
            source.suffix + ".saar"
        )
        args = ["compress", str(source), "-o", str(target)]
        if profile:
            args += ["--profile", profile]
        if modality:
            args += ["--modality", modality]
        return self._run(args)

    def decompress(
        self,
        input_path: str | os.PathLike[str],
        output_path: str | os.PathLike[str] | None = None,
    ) -> SaarResult:
        """Restore one artifact."""
        source = Path(input_path)
        if output_path is not None:
            target = Path(output_path)
        elif source.suffix == ".saar":
            target = source.with_suffix("")
        else:
            target = source.with_suffix(source.suffix + ".out")
        return self._run(["decompress", str(source), "-o", str(target)])

    def verify(self, input_path: str | os.PathLike[str]) -> SaarResult:
        """Round-trip integrity check. ``result.ok`` reports the verdict."""
        return self._run(["verify", str(Path(input_path))])

    def detect(self, input_path: str | os.PathLike[str]) -> SaarResult:
        """Report the detected modality without compressing."""
        return self._run(["detect", str(Path(input_path))])

    def info(self, input_path: str | os.PathLike[str]) -> SaarResult:
        """Describe an existing artifact."""
        return self._run(["info", str(Path(input_path))])
