"""Exception types raised by the Saar SDK."""

from __future__ import annotations


class SaarError(RuntimeError):
    """Base error for every Saar SDK failure."""


class SaarBinaryNotFound(SaarError):
    """The Saar CLI could not be located.

    Set SAAR_BIN to an absolute path, or install the CLI so that ``saar`` is
    on PATH.
    """


class SaarCommandError(SaarError):
    """The Saar CLI ran but reported failure.

    Attributes:
        command: the argv that was executed.
        exit_code: the process exit status.
        stderr: captured standard error, if any.
    """

    def __init__(self, command: list[str], exit_code: int, stderr: str) -> None:
        self.command = command
        self.exit_code = exit_code
        self.stderr = stderr
        detail = stderr.strip() or "no diagnostic output"
        super().__init__(f"{' '.join(command)} failed with exit {exit_code}: {detail}")


class SaarProtocolError(SaarError):
    """The CLI returned output the SDK could not interpret as JSON."""
