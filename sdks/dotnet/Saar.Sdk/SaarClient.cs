using System.Diagnostics;
using System.Text;
using System.Text.Json;

namespace Heossi.Saar;

/// <summary>
/// A thin client over the Saar command-line interface.
/// </summary>
/// <remarks>
/// It does not reimplement the codec. It invokes the installed <c>saar</c>
/// binary and decodes the JSON it prints, which is the same boundary the
/// desktop application uses, so one codec implementation stays authoritative.
/// </remarks>
public sealed class SaarClient
{
    private static readonly TimeSpan DefaultTimeout = TimeSpan.FromHours(1);

    private readonly string _binary;
    private readonly TimeSpan _timeout;

    private SaarClient(string binary, TimeSpan timeout)
    {
        _binary = binary;
        _timeout = timeout;
    }

    /// <summary>Locate the CLI via <c>SAAR_BIN</c>, then <c>saar</c> on PATH.</summary>
    public static SaarClient Create() => Create(null, DefaultTimeout);

    /// <summary>Locate the CLI, optionally at an explicit path.</summary>
    public static SaarClient Create(string? explicitPath, TimeSpan? timeout = null)
    {
        var resolved = explicitPath;

        if (resolved is null)
        {
            var env = Environment.GetEnvironmentVariable("SAAR_BIN");
            if (!string.IsNullOrWhiteSpace(env))
            {
                resolved = env.Trim();
                if (!File.Exists(resolved))
                {
                    throw new SaarBinaryNotFoundException($"SAAR_BIN is not a file: {resolved}");
                }
            }
        }

        if (resolved is null)
        {
            var path = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
            foreach (var dir in path.Split(Path.PathSeparator))
            {
                if (string.IsNullOrWhiteSpace(dir)) continue;
                foreach (var name in new[] { "saar", "saar.exe" })
                {
                    var candidate = Path.Combine(dir, name);
                    if (File.Exists(candidate))
                    {
                        resolved = candidate;
                        break;
                    }
                }
                if (resolved is not null) break;
            }
        }

        if (resolved is null)
        {
            throw new SaarBinaryNotFoundException(
                "CLI not found; set SAAR_BIN or put 'saar' on PATH");
        }

        return new SaarClient(resolved, timeout ?? DefaultTimeout);
    }

    /// <summary>Absolute path of the CLI in use.</summary>
    public string Binary => _binary;

    /// <summary>CLI version string.</summary>
    public string Version()
    {
        var result = Execute(["--version"]);
        var text = result.Stdout.Trim();
        if (text.Length == 0)
        {
            throw new SaarException("saar: the CLI printed no version");
        }
        return text;
    }

    /// <summary>Compress one file. <paramref name="output"/> null writes &lt;input&gt;.saar.</summary>
    public SaarResult Compress(string input, string? output = null)
    {
        var target = output ?? input + ".saar";
        return Execute(["compress", input, "-o", target]);
    }

    /// <summary>Restore one artifact. <paramref name="output"/> null strips .saar.</summary>
    public SaarResult Decompress(string input, string? output = null)
    {
        var target = output ?? DefaultRestorePath(input);
        return Execute(["decompress", input, "-o", target]);
    }

    /// <summary>Round-trip integrity check.</summary>
    public SaarResult Verify(string input) => Execute(["verify", input]);

    /// <summary>Detected modality, without compressing.</summary>
    public SaarResult Detect(string input) => Execute(["detect", input]);

    /// <summary>Describe an existing artifact.</summary>
    public SaarResult Info(string input) => Execute(["info", input]);

    private static string DefaultRestorePath(string input) =>
        input.EndsWith(".saar", StringComparison.Ordinal)
            ? input[..^".saar".Length]
            : input + ".out";

    private SaarResult Execute(IReadOnlyList<string> args)
    {
        var command = new List<string>(args.Count + 1) { _binary };
        command.AddRange(args);

        var start = new ProcessStartInfo(_binary)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
        };
        foreach (var arg in args) start.ArgumentList.Add(arg);

        using var process = Process.Start(start)
            ?? throw new SaarBinaryNotFoundException($"could not start {_binary}");

        var stderrTask = process.StandardError.ReadToEndAsync();
        var stdout = process.StandardOutput.ReadToEnd();

        if (!process.WaitForExit((int)_timeout.TotalMilliseconds))
        {
            try { process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { }
            throw new SaarException(
                $"saar: timed out after {_timeout.TotalSeconds:0}s: {string.Join(' ', command)}");
        }

        var stderr = stderrTask.GetAwaiter().GetResult();
        if (process.ExitCode != 0)
        {
            throw new SaarCommandException(command, process.ExitCode, stderr);
        }

        return SaarResult.Parse(command, stdout);
    }
}

/// <summary>Outcome of one CLI invocation.</summary>
public sealed class SaarResult
{
    private SaarResult(IReadOnlyList<string> command, string? output, string stdout, IReadOnlyDictionary<string, string> values)
    {
        Command = command;
        Output = output;
        Stdout = stdout;
        Values = values;
    }

    /// <summary>The argv that ran.</summary>
    public IReadOnlyList<string> Command { get; }

    /// <summary>The artifact or restored path the CLI reported, if any.</summary>
    public string? Output { get; }

    /// <summary>Raw standard output.</summary>
    public string Stdout { get; }

    /// <summary>Flat key/value view of the JSON object.</summary>
    public IReadOnlyDictionary<string, string> Values { get; }

    /// <summary>Reports the CLI verdict; commands without a verdict return true.</summary>
    public bool Ok => !Values.TryGetValue("ok", out var verdict)
        || bool.TryParse(verdict, out var parsed) && parsed;

    internal static SaarResult Parse(IReadOnlyList<string> command, string stdout)
    {
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        var text = stdout.Trim();
        if (text.StartsWith('{'))
        {
            try
            {
                using var document = JsonDocument.Parse(text);
                foreach (var property in document.RootElement.EnumerateObject())
                {
                    values[property.Name] = property.Value.ValueKind switch
                    {
                        JsonValueKind.String => property.Value.GetString() ?? string.Empty,
                        JsonValueKind.True => "true",
                        JsonValueKind.False => "false",
                        JsonValueKind.Number => property.Value.GetRawText(),
                        _ => property.Value.GetRawText(),
                    };
                }
            }
            catch (JsonException)
            {
                // Leave values empty; callers that need a verdict will see no claim.
            }
        }

        return new SaarResult(
            command,
            values.TryGetValue("output", out var output) ? output : null,
            stdout,
            values);
    }
}
