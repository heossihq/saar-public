using Heossi.Saar;
using Xunit;

namespace Heossi.Saar.Tests;

public sealed class SaarClientTests : IDisposable
{
    private static readonly byte[] Sample =
        System.Text.Encoding.UTF8.GetBytes("the quick brown fox jumps over the lazy dog\n");

    private readonly string _dir;
    private readonly SaarClient _client;

    public SaarClientTests()
    {
        _dir = Path.Combine(Path.GetTempPath(), "saar-dotnet-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_dir);
        _client = SaarClient.Create();
    }

    public void Dispose()
    {
        try { Directory.Delete(_dir, recursive: true); } catch (IOException) { }
    }

    private string WriteSample(string name = "sample.txt")
    {
        var path = Path.Combine(_dir, name);
        File.WriteAllBytes(path, Sample);
        return path;
    }

    [Fact]
    public void VersionIsReported()
    {
        Assert.Matches(@"^\d+\.\d+\.\d+", _client.Version());
    }

    [Fact]
    public void RoundTripIsByteExact()
    {
        var source = WriteSample();
        var compressed = _client.Compress(source);
        Assert.NotNull(compressed.Output);
        Assert.True(File.Exists(compressed.Output));

        var restored = Path.Combine(_dir, "restored.txt");
        _client.Decompress(compressed.Output!, restored);
        Assert.Equal(Sample, File.ReadAllBytes(restored));
    }

    [Fact]
    public void VerifyReportsOk()
    {
        var artifact = _client.Compress(WriteSample()).Output!;
        Assert.True(_client.Verify(artifact).Ok);
    }

    [Fact]
    public void MissingInputFails()
    {
        var error = Assert.Throws<SaarCommandException>(
            () => _client.Compress(Path.Combine(_dir, "absent.txt")));
        Assert.NotEqual(0, error.ExitCode);
    }

    [Fact]
    public void CorruptArtifactRejected()
    {
        var bad = Path.Combine(_dir, "corrupt.saar");
        File.WriteAllText(bad, "not a saar artifact");
        Assert.ThrowsAny<SaarException>(() => _client.Decompress(bad, Path.Combine(_dir, "out.bin")));
    }

    [Fact]
    public void RefusesToOverwrite()
    {
        var artifact = _client.Compress(WriteSample("a.txt")).Output!;
        var occupied = Path.Combine(_dir, "occupied.txt");
        File.WriteAllText(occupied, "keep me");
        Assert.ThrowsAny<SaarException>(() => _client.Decompress(artifact, occupied));
        Assert.Equal("keep me", File.ReadAllText(occupied));
    }
}
