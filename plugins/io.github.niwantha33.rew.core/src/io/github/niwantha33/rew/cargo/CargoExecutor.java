// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bounded, cancellable Cargo runner, with no shell or privilege escalation. */
public final class CargoExecutor {
    public record Result(int exitCode, boolean truncated, long elapsedMillis, String log) {
        public boolean succeeded() { return exitCode == 0; }
    }
    @FunctionalInterface
    public interface Runner {
        Result execute(CargoPlan plan, BooleanSupplier cancelled,
                       Consumer<String> output) throws IOException, InterruptedException;
    }

    private final Runner runner;
    public CargoExecutor(Runner runner) {
        if (runner == null) throw new IllegalArgumentException("Missing Cargo runner");
        this.runner = runner;
    }

    public Result run(CargoPlan plan, boolean confirmed, BooleanSupplier cancelled,
            Consumer<String> output) throws IOException, InterruptedException {
        if (!confirmed) throw new SecurityException("Cargo command requires explicit approval.");
        if (plan == null || cancelled == null || output == null)
            throw new IllegalArgumentException("Incomplete Cargo execution context.");
        if (cancelled.getAsBoolean())
            throw new InterruptedIOException("Cargo command cancelled before start.");
        return runner.execute(plan, cancelled, output);
    }

    public static final class LocalRunner implements Runner {
        private static final Duration TIMEOUT = Duration.ofMinutes(20);
        private static final int MAX_LOG_CHARS = 256 * 1024;

        @Override public Result execute(CargoPlan plan, BooleanSupplier cancelled,
                Consumer<String> output) throws IOException, InterruptedException {
            List<String> argv = plan.command();
            if (argv.isEmpty() || !"cargo".equals(argv.get(0)))
                throw new SecurityException("Unexpected Cargo executable.");
            List<String> launch = new java.util.ArrayList<>(argv);
            launch.set(0, CargoToolResolver.current().toString());
            ProcessBuilder builder = new ProcessBuilder(launch);
            builder.directory(plan.project().directory().toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.getOutputStream().close();
            long started = System.nanoTime();
            StringBuilder log = new StringBuilder();
            AtomicReference<IOException> readError = new AtomicReference<>();
            AtomicReference<RuntimeException> outputError = new AtomicReference<>();
            AtomicBoolean truncated = new AtomicBoolean();
            Thread reader = Thread.ofVirtual().start(() -> {
                try (BufferedReader input = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    char[] buffer = new char[2048];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        String piece = new String(buffer, 0, count);
                        int allowed;
                        synchronized (log) {
                            allowed = Math.min(count, MAX_LOG_CHARS - log.length());
                            if (allowed > 0) log.append(buffer, 0, allowed);
                        }
                        if (allowed < count) truncated.set(true);
                        if (allowed > 0 && outputError.get() == null) {
                            try {
                                output.accept(piece.substring(0, allowed));
                            } catch (RuntimeException error) {
                                // Keep draining the subprocess, even when the Console is closed.
                                outputError.set(error);
                            }
                        }
                    }
                } catch (IOException error) {
                    readError.set(error);
                }
            });
            try {
                while (!process.waitFor(150, TimeUnit.MILLISECONDS)) {
                    if (cancelled.getAsBoolean())
                        throw new InterruptedIOException("Cargo command cancelled.");
                    if (System.nanoTime() - started > TIMEOUT.toNanos())
                        throw new IOException("Cargo execution timed out after 20 minutes.");
                }
                reader.join(5_000);
                if (reader.isAlive()) throw new IOException("Cargo log reader did not finish.");
                if (readError.get() != null)
                    throw new IOException("Cargo log read failed.", readError.get());
                if (outputError.get() != null)
                    throw new IOException("Eclipse Cargo Console output failed.", outputError.get());
                if (truncated.get()) output.accept("\n[Cargo output truncated at 256 KiB]\n");
                return new Result(process.exitValue(), truncated.get(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        log.toString());
            } finally {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroy);
                    process.destroy();
                    if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                        process.descendants().forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
                    }
                }
            }
        }
    }
}
