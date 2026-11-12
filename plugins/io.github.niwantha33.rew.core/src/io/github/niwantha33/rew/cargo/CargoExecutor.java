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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bounded, cancellable subprocess wrapper; independent of Eclipse/OS.
 * Does not interpret user-controlled shell strings or claim firmware success.
 */
public final class CargoExecutor {
    public record Result(int exitCode, boolean truncated, long elapsedMillis,
                         String log) {
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

    public Result run(CargoPlan plan, boolean confirmed,
            BooleanSupplier cancelled, Consumer<String> output)
            throws IOException, InterruptedException {
        if (!confirmed) throw new SecurityException("Cargo command requires confirmation.");
        if (plan == null || cancelled == null || output == null)
            throw new IllegalArgumentException("Missing Cargo command context.");
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
                throw new SecurityException("Only the fixed Cargo executable is permitted.");
            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.directory(plan.project().directory().toFile());
            builder.redirectErrorStream(true);
            // No shell, no elevation, no inherited interactive input.
            Process process = builder.start();
            process.getOutputStream().close();
            long started = System.nanoTime();
            StringBuilder log = new StringBuilder();
            AtomicReference<IOException> readerError = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean truncated = new java.util.concurrent.atomic.AtomicBoolean();
            Thread reader = Thread.ofVirtual().start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        synchronized (log) {
                            if (log.length() < MAX_LOG_CHARS) {
                                int allowed = Math.min(line.length(), MAX_LOG_CHARS - log.length());
                                log.append(line, 0, allowed);
                                if (log.length() < MAX_LOG_CHARS) log.append('\n');
                            } else truncated.set(true);
                        }
                        if (!truncated.get()) output.accept(line + "\n");
                    }
                } catch (IOException e) {
                    readerError.set(e);
                }
            });
            try {
                while (!process.waitFor(150, TimeUnit.MILLISECONDS)) {
                    if (cancelled.getAsBoolean())
                        throw new InterruptedIOException("Cargo command cancelled.");
                    if (System.nanoTime() - started > TIMEOUT.toNanos())
                        throw new IOException("Cargo command reached the 20-minute timeout.");
                }
                reader.join(5_000);
                if (reader.isAlive()) throw new IOException("Cargo output reader did not finish.");
                if (readerError.get() != null)
                    throw new IOException("Cargo output capture failed.", readerError.get());
                if (truncated.get()) output.accept("\n[Cargo log truncated at 256 KiB]\n");
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
