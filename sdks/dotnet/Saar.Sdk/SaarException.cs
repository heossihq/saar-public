namespace Heossi.Saar;

/// <summary>Base type for every Saar SDK failure.</summary>
public class SaarException : Exception
{
    /// <summary>Creates the base exception.</summary>
    public SaarException(string message) : base(message) { }

    /// <summary>Creates the base exception with an inner cause.</summary>
    public SaarException(string message, Exception inner) : base(message, inner) { }
}

/// <summary>The Saar CLI could not be located.</summary>
public sealed class SaarBinaryNotFoundException : SaarException
{
    /// <summary>Creates the exception.</summary>
    public SaarBinaryNotFoundException(string message) : base(message) { }
}

/// <summary>The CLI ran but exited non-zero.</summary>
public sealed class SaarCommandException : SaarException
{
    /// <summary>Creates the exception from a failed invocation.</summary>
    public SaarCommandException(IReadOnlyList<string> command, int exitCode, string stderr)
        : base(Describe(command, exitCode, stderr))
    {
        Command = command;
        ExitCode = exitCode;
        Stderr = stderr ?? string.Empty;
    }

    /// <summary>The argv that ran.</summary>
    public IReadOnlyList<string> Command { get; }

    /// <summary>The process exit status.</summary>
    public int ExitCode { get; }

    /// <summary>Captured standard error, if any.</summary>
    public string Stderr { get; }

    private static string Describe(IReadOnlyList<string> command, int exitCode, string? stderr)
    {
        var detail = string.IsNullOrWhiteSpace(stderr) ? "no diagnostic output" : stderr.Trim();
        return $"saar: {string.Join(' ', command)} failed with exit {exitCode}: {detail}";
    }
}
