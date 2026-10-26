"""Saar SDK for Python.

Byte-exact lossless compression through the official Saar command-line
interface. This package wraps the CLI; it does not reimplement the codec.
"""

from __future__ import annotations

from .client import SaarClient, SaarResult
from .errors import (
    SaarBinaryNotFound,
    SaarCommandError,
    SaarError,
    SaarProtocolError,
)

__version__ = "1.3.0"
__all__ = [
    "SaarBinaryNotFound",
    "SaarClient",
    "SaarCommandError",
    "SaarError",
    "SaarProtocolError",
    "SaarResult",
    "__version__",
]
