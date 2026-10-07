package com.codexbar.android.core.nativecli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Reused from the verified acceptance harness. Only direct children are managed:
// production callers must select API sources, never desktop CLI/PTY sources.
public final class CliProcess {
    private static final int OUTPUT_LIMIT = 1024 * 1024;

    public static final class Result {
        public final int exitCode;
        public final boolean timedOut;
        public final long durationMs;
        public final String stdout;
        public final String stderr;
        public final boolean stdoutTruncated;
        public final boolean stderrTruncated;

        public Result(int exitCode, boolean timedOut, long durationMs, String stdout, String stderr,
                      boolean stdoutTruncated, boolean stderrTruncated) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.durationMs = durationMs;
            this.stdout = stdout;
            this.stderr = stderr;
            this.stdoutTruncated = stdoutTruncated;
            this.stderrTruncated = stderrTruncated;
        }
    }

    private static final class Capture {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean truncated;

        void drain(InputStream stream, AtomicBoolean terminating) throws Exception {
            byte[] buffer = new byte[8192];
            int count;
            try {
                while ((count = stream.read(buffer)) != -1) {
                    int retained = Math.min(count, OUTPUT_LIMIT - bytes.size());
                    bytes.write(buffer, 0, retained);
                    truncated |= retained < count;
                }
            } catch (InterruptedIOException e) {
                if (!terminating.get()) throw e;
            }
        }

        String text() {
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static Result run(File executable, List<String> arguments, Map<String, String> environment,
                          File cwd, long timeoutMillis) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd);
        builder.environment().clear();
        builder.environment().putAll(environment);
        long started = System.nanoTime();
        Process process = builder.start();
        var pumps = Executors.newFixedThreadPool(2);
        Capture stdout = new Capture();
        Capture stderr = new Capture();
        AtomicBoolean terminating = new AtomicBoolean();
        var out = pumps.submit(() -> { stdout.drain(process.getInputStream(), terminating); return null; });
        var err = pumps.submit(() -> { stderr.drain(process.getErrorStream(), terminating); return null; });
        try {
            process.getOutputStream().close();
            boolean timedOut = !process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (timedOut) {
                terminating.set(true);
                process.destroyForcibly();
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("Child not reaped");
            out.get(5, TimeUnit.SECONDS);
            err.get(5, TimeUnit.SECONDS);
            return new Result(process.exitValue(), timedOut,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                stdout.text(), stderr.text(), stdout.truncated, stderr.truncated);
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                if (process.isAlive()) {
                    terminating.set(true);
                    process.destroyForcibly();
                }
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("Child still alive");
            } finally {
                closeQuietly(process.getOutputStream());
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                pumps.shutdownNow();
                try {
                    if (!pumps.awaitTermination(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("CLI output readers did not stop");
                    }
                } finally {
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void closeQuietly(Closeable stream) {
        try { stream.close(); } catch (IOException ignored) { /* Child is already reaped. */ }
    }
}
