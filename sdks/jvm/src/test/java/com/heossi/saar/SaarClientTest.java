package com.heossi.saar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaarClientTest {

    private static final byte[] SAMPLE = "the quick brown fox jumps over the lazy dog\n".getBytes();

    private static SaarClient client;

    @BeforeAll
    static void setUp() {
        client = SaarClient.create();
    }

    @Test
    void versionIsReported() {
        assertTrue(client.version().matches("\\d+\\.\\d+\\.\\d+.*"));
    }

    @Test
    void roundTripIsByteExact(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("sample.txt");
        Files.write(source, SAMPLE);

        SaarClient.SaarResult compressed = client.compress(source, null);
        assertNotNull(compressed.output());
        assertTrue(Files.isRegularFile(compressed.output()));

        Path restored = dir.resolve("restored.txt");
        client.decompress(compressed.output(), restored);
        assertArrayEquals(SAMPLE, Files.readAllBytes(restored));
    }

    @Test
    void verifyReportsOk(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("v.txt");
        Files.write(source, SAMPLE);
        Path artifact = client.compress(source, null).output();
        assertTrue(client.verify(artifact).ok());
    }

    @Test
    void missingInputFails(@TempDir Path dir) {
        SaarException.CommandFailed error = assertThrows(
                SaarException.CommandFailed.class,
                () -> client.compress(dir.resolve("absent.txt"), null));
        assertTrue(error.exitCode() != 0);
    }

    @Test
    void corruptArtifactRejected(@TempDir Path dir) throws IOException {
        Path bad = dir.resolve("corrupt.saar");
        Files.write(bad, "not a saar artifact".getBytes());
        assertThrows(SaarException.class, () -> client.decompress(bad, dir.resolve("out.bin")));
    }

    @Test
    void refusesToOverwrite(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("a.txt");
        Files.write(source, SAMPLE);
        Path artifact = client.compress(source, null).output();

        Path occupied = dir.resolve("occupied.txt");
        Files.write(occupied, "keep me".getBytes());
        assertThrows(SaarException.class, () -> client.decompress(artifact, occupied));
        assertEquals("keep me", Files.readString(occupied));
    }
}
