// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import io.github.niwantha33.rew.core.BoardRegistry;

/**
 * Explicitly selected, catalog-owned setup commands only. Never uses a shell,
 * arbitrary executable, administrator escalation, driver replacement or flash.
 */
public final class SetupExecutor {
    public record Result(int exitCode, String output) {
        public boolean succeeded() { return exitCode == 0; }
    }
    @FunctionalInterface public interface Launcher {
        Result run(List<String> argv, Duration timeout, BooleanSupplier cancelled)
                throws IOException, InterruptedException;
    }

    private final Launcher launcher;
    public SetupExecutor(Launcher launcher) {
        if (launcher == null) throw new IllegalArgumentException("Missing command launcher");
        this.launcher = launcher;
    }

    public Result install(BoardRegistry boards, String boardId, String stepId,
            boolean userConfirmed, BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (!userConfirmed) throw new SecurityException("Installation requires explicit user approval");
        if (boards == null || cancelled == null) throw new IllegalArgumentException("Missing setup context");
        var board = boards.require(boardId);
        var recipe = SetupCatalog.require(SetupCatalog.forBoard(board, isWindows()), stepId);
        if (recipe.mode() != SetupCatalog.Mode.MANAGED)
            throw new SecurityException("Driver installation is manual and requires vendor verification");
        List<String> argv = recipe.command();
        if (!List.of("winget", "rustup", "cargo", "espup").contains(argv.get(0)))
            throw new SecurityException("Executable is not in the setup allowlist");
        if (argv.get(0).equals("winget") && !isWindows())
            throw new SecurityException("Windows installer cannot run on another OS");
        if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Setup cancelled");
        return launcher.run(argv, Duration.ofMinutes(15), cancelled);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Runs a fixed argument vector with bounded output, wall time, and cancellation. */
    public static final class LocalProcess implements Launcher {
        private static final int MAX_LOG_BYTES = 48 * 1024;
        @Override public Result run(List<String> argv, Duration timeout,
                BooleanSupplier cancelled) throws IOException, InterruptedException {
            if (argv == null || argv.isEmpty() || timeout == null
                    || timeout.isNegative() || timeout.isZero()
                    || timeout.compareTo(Duration.ofMinutes(15)) > 0)
                throw new IllegalArgumentException("Invalid process bounds");
            if (cancelled.getAsBoolean())
                throw new java.io.InterruptedIOException("Setup cancelled");
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectErrorStream(true);
            pb.directory(Path.of(System.getProperty("user.home")).toFile());
            // Inherit no standard input; package managers must ask via a trusted OS UI
            // instead of silently consuming arbitrary Eclipse console input.
            Process process = pb.start();
            process.getOutputStream().close();
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            AtomicReference<IOException> readFailure = new AtomicReference<>();
            Thread drain = Thread.ofVirtual().start(() -> {
                try (InputStream stream = process.getInputStream()) {
                    byte[] chunk = new byte[2048];
                    int count;
                    while ((count = stream.read(chunk)) != -1) {
                        if (log.size() < MAX_LOG_BYTES) {
                            synchronized (log) {
                                log.write(chunk, 0, Math.min(count, MAX_LOG_BYTES - log.size()));
                            }
                        }
                    }
                } catch (IOException error) {
                    readFailure.set(error);
                }
            });
            long deadline = System.nanoTime() + timeout.toNanos();
            try {
                while (!process.waitFor(150, TimeUnit.MILLISECONDS)) {
                    if (cancelled.getAsBoolean())
                        throw new java.io.InterruptedIOException("Setup cancelled by user");
                    if (System.nanoTime() >= deadline)
                        throw new IOException("Setup timed out; process was stopped");
                }
                drain.join(3000);
                if (drain.isAlive())
                    throw new IOException("Setup output reader did not finish");
                if (readFailure.get() != null)
                    throw new IOException("Unable to capture setup result", readFailure.get());
                String message;
                synchronized (log) {
                    message = log.toString(StandardCharsets.UTF_8);
                }
                if (log.size() >= MAX_LOG_BYTES)
                    message += "\n[output truncated at 48 KiB]";
                return new Result(process.exitValue(), message);
            } finally {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS))
                        process.destroyForcibly();
                }
            }
        }
    }
}
