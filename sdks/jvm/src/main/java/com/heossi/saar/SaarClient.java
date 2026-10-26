package com.heossi.saar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A thin client over the Saar command-line interface.
 *
 * <p>It does not reimplement the codec. It invokes the installed {@code saar}
 * binary and decodes the JSON it prints, which is the same boundary the desktop
 * application uses, so one codec implementation stays authoritative.
 *
 * <pre>{@code
 * SaarClient client = SaarClient.create();
 * SaarResult compressed = client.compress(Path.of("report.pdf"), null);
 * client.decompress(compressed.output(), Path.of("restored.pdf"));
 * }</pre>
 */
public final class SaarClient {

    private static final long DEFAULT_TIMEOUT_SECONDS = 3600;

    private final Path binary;
    private final long timeoutSeconds;

    private SaarClient(Path binary, long timeoutSeconds) {
        this.binary = binary;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Create a client using {@code SAAR_BIN}, then {@code saar} on PATH. */
    public static SaarClient create() {
        return create(null, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Create a client.
     *
     * @param explicit explicit CLI path, or {@code null} to resolve automatically
     * @param timeoutSeconds per-invocation timeout
     */
    public static SaarClient create(Path explicit, long timeoutSeconds) {
        Path resolved = explicit;
        if (resolved == null) {
            String env = System.getenv("SAAR_BIN");
            if (env != null && !env.isBlank()) {
                resolved = Path.of(env.trim());
                if (!Files.isRegularFile(resolved)) {
                    throw new SaarException.BinaryNotFound("SAAR_BIN is not a file: " + resolved);
                }
            }
        }
        if (resolved == null) {
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                    Path candidate = Path.of(dir, "saar");
                    if (Files.isRegularFile(candidate)) {
                        resolved = candidate;
                        break;
                    }
                }
            }
        }
        if (resolved == null) {
            throw new SaarException.BinaryNotFound(
                    "CLI not found; set SAAR_BIN or put 'saar' on PATH");
        }
        return new SaarClient(resolved, timeoutSeconds);
    }

    /** Absolute path of the CLI in use. */
    public Path binary() {
        return binary;
    }

    /** CLI version string. */
    public String version() {
        SaarResult result = execute(List.of("--version"));
        String text = result.stdout().trim();
        if (text.isEmpty()) {
            throw new SaarException("saar: the CLI printed no version");
        }
        return text;
    }

    /**
     * Compress one file.
     *
     * @param input source file
     * @param output target artifact, or {@code null} for {@code <input>.saar}
     */
    public SaarResult compress(Path input, Path output) {
        Path target = output != null ? output : Path.of(input + ".saar");
        return execute(List.of("compress", input.toString(), "-o", target.toString()));
    }

    /**
     * Restore one artifact.
     *
     * @param input artifact
     * @param output target file, or {@code null} to strip {@code .saar}
     */
    public SaarResult decompress(Path input, Path output) {
        Path target = output != null ? output : defaultRestorePath(input);
        return execute(List.of("decompress", input.toString(), "-o", target.toString()));
    }

    /** Round-trip integrity check. */
    public SaarResult verify(Path input) {
        return execute(List.of("verify", input.toString()));
    }

    /** Detected modality, without compressing. */
    public SaarResult detect(Path input) {
        return execute(List.of("detect", input.toString()));
    }

    /** Describe an existing artifact. */
    public SaarResult info(Path input) {
        return execute(List.of("info", input.toString()));
    }

    private static Path defaultRestorePath(Path input) {
        String name = input.toString();
        if (name.endsWith(".saar")) {
            return Path.of(name.substring(0, name.length() - ".saar".length()));
        }
        return Path.of(name + ".out");
    }

    private SaarResult execute(List<String> args) {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(binary.toString());
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new SaarException.BinaryNotFound("could not start " + binary + ": " + e.getMessage());
        }

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SaarException("saar: timed out after " + timeoutSeconds + "s: "
                        + String.join(" ", command));
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.exitValue();
            if (exit != 0) {
                throw new SaarException.CommandFailed(command, exit, stderr);
            }
            return SaarResult.parse(command, stdout);
        } catch (IOException e) {
            throw new SaarException("saar: failed reading process output: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SaarException("saar: interrupted", e);
        }
    }

    /**
     * Outcome of one CLI invocation.
     *
     * @param command the argv that ran
     * @param output the artifact or restored path the CLI reported
     * @param stdout raw standard output
     * @param values flat key/value view of the JSON object
     */
    public record SaarResult(List<String> command, Path output, String stdout, Map<String, String> values) {

        /** Reports the CLI verdict; commands without a verdict return true. */
        public boolean ok() {
            String verdict = values.get("ok");
            return verdict == null || Boolean.parseBoolean(verdict);
        }

        static SaarResult parse(List<String> command, String stdout) {
            Map<String, String> values = flatten(stdout);
            String output = values.get("output");
            return new SaarResult(
                    List.copyOf(command),
                    output == null ? null : Path.of(output),
                    stdout,
                    values);
        }

        /**
         * Flatten the flat JSON object the CLI prints.
         *
         * <p>The CLI emits scalars only, so a full JSON parser would be excess
         * dependency weight for an SDK that needs {@code output} and {@code ok}.
         */
        static Map<String, String> flatten(String stdout) {
            Map<String, String> map = new LinkedHashMap<>();
            String text = stdout == null ? "" : stdout.trim();
            if (!text.startsWith("{")) {
                return map;
            }
            String body = text.substring(1, text.endsWith("}") ? text.length() - 1 : text.length());
            for (String line : body.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.endsWith(",")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                int colon = trimmed.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                String value = trimmed.substring(colon + 1).trim().replaceAll("^\"|\"$", "");
                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
            return map;
        }
    }
}
