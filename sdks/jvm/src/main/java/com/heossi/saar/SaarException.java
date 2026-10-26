package com.heossi.saar;

/** Base type for every Saar SDK failure. */
public class SaarException extends RuntimeException {

    public SaarException(String message) {
        super(message);
    }

    public SaarException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The Saar CLI could not be located. */
    public static final class BinaryNotFound extends SaarException {
        public BinaryNotFound(String message) {
            super(message);
        }
    }

    /** The CLI ran but exited non-zero. */
    public static final class CommandFailed extends SaarException {

        private final transient java.util.List<String> command;
        private final int exitCode;
        private final String stderr;

        public CommandFailed(java.util.List<String> command, int exitCode, String stderr) {
            super(describe(command, exitCode, stderr));
            this.command = java.util.List.copyOf(command);
            this.exitCode = exitCode;
            this.stderr = stderr == null ? "" : stderr;
        }

        private static String describe(java.util.List<String> command, int exitCode, String stderr) {
            String detail = stderr == null || stderr.isBlank() ? "no diagnostic output" : stderr.trim();
            return "saar: " + String.join(" ", command) + " failed with exit " + exitCode + ": " + detail;
        }

        public java.util.List<String> command() {
            return command;
        }

        public int exitCode() {
            return exitCode;
        }

        public String stderr() {
            return stderr;
        }
    }
}
