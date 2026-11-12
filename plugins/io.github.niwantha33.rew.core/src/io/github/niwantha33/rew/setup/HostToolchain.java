// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Host-only read-only discovery. Presence/version != embedded hardware qualification. */
public final class HostToolchain {
    private HostToolchain() {}

    private static final Set<String> ALLOWED = Set.of(
            "winget", "rustup", "cargo", "rustc", "rust-analyzer", "probe-rs", "espflash", "espup");
    private static final Set<String> COMPONENTS = Set.of("rust-src", "rust-analyzer", "clippy", "rustfmt");
    private static final int MAX_OUTPUT = 16 * 1024;

    public enum State { AVAILABLE, MISSING, BROKEN }
    public record Tool(String name, State state, Path executable, String version) {
        public boolean usable() { return state == State.AVAILABLE; }
        public String summary() {
            return name + ": " + (usable() ? version : (state == State.MISSING
                    ? "not found" : "found but cannot execute")) +
                    (executable == null ? "" : " [" + executable + "]");
        }
    }
    public record Report(Map<String, Tool> tools, Set<String> installedComponents,
                         boolean componentListVerified) {
        public Report {
            tools = Map.copyOf(tools);
            installedComponents = Set.copyOf(installedComponents);
        }
        public boolean available(String name) {
            Tool tool = tools.get(name);
            return tool != null && tool.usable();
        }
        public boolean readyForCargo() {
            return available("rustc") && available("cargo");
        }
        public boolean componentInstalled(String name) {
            return installedComponents.contains(name);
        }
        public String summary() {
            return "Rust host: " + (readyForCargo() ? "READY for host Cargo commands"
                    : "NOT READY — install or repair Rust/Cargo")
                    + "\n" + tools.values().stream()
                            .map(Tool::summary)
                            .collect(java.util.stream.Collectors.joining("\n"))
                    + "\nComponents: " + (componentListVerified()
                    ? (installedComponents.isEmpty() ? "(none found)" :
                            String.join(", ", installedComponents))
                    : "not verified — check rustup installation")
                    + "\nBoard targets, HAL, USB drivers and debugger support require separate verification.";
        }
    }

    /** The final executable name is preserved: rustup dispatches Cargo by argv[0]. */
    public static Path locate(String name, String pathEnv, Path home, boolean windows) {
        if (!ALLOWED.contains(name)) throw new IllegalArgumentException("Unapproved tool name");
        List<Path> dirs = new ArrayList<>();
        if (!"winget".equals(name) && home != null)
            dirs.add(home.resolve(".cargo").resolve("bin"));
        if (pathEnv != null) {
            for (String entry : pathEnv.split(windows ? ";" : ":", -1)) {
                if (entry.isBlank()) continue;
                try {
                    Path dir = Path.of(entry);
                    if (dir.isAbsolute()) dirs.add(dir);
                } catch (RuntimeException ignored) { /* malformed PATH component */ }
            }
        }
        List<String> extensions = windows ? List.of(".exe", ".cmd", ".bat") : List.of("");
        for (Path dir : dirs) {
            for (String ext : extensions) {
                Path candidate = dir.resolve(name + ext);
                if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate)))
                    return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public static Path current(String name) {
        return locate(name, System.getenv("PATH"),
                Path.of(System.getProperty("user.home")), SetupExecutor.isWindows());
    }

    private static String command(List<String> arguments, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        if (arguments.isEmpty() || !ALLOWED.contains(arguments.get(0)))
            throw new SecurityException("Unknown host tool");
        Path tool = current(arguments.get(0));
        if (tool == null) throw new IOException("Host tool is not installed");
        List<String> exact = new ArrayList<>(arguments);
        exact.set(0, tool.toString());
        Process process = new ProcessBuilder(exact).redirectErrorStream(true).start();
        process.getOutputStream().close();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    synchronized (out) {
                        if (out.size() < MAX_OUTPUT)
                            out.write(buffer, 0, Math.min(n, MAX_OUTPUT - out.size()));
                    }
                }
            } catch (IOException ignored) {
                // Exit status and final report indicate the tool was not verified.
            }
        });
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        try {
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Tool check cancelled");
                if (System.nanoTime() > deadline) throw new IOException("Tool version check timed out");
            }
            reader.join(1500);
            if (reader.isAlive() || process.exitValue() != 0)
                throw new IOException("Tool returned a nonzero version-check status");
            synchronized (out) {
                return out.toString(StandardCharsets.UTF_8).strip();
            }
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
            }
        }
    }

    public static Report inspect(BooleanSupplier cancelled) throws InterruptedException {
        Map<String, Tool> tools = new LinkedHashMap<>();
        for (String name : List.of("rustup", "cargo", "rustc", "rust-analyzer", "winget",
                "probe-rs", "espflash")) {
            if (cancelled.getAsBoolean()) break;
            Path executable = current(name);
            if (executable == null) {
                tools.put(name, new Tool(name, State.MISSING, null, ""));
                continue;
            }
            try {
                String result = command(List.of(name, "--version"), cancelled);
                String first = result.lines().findFirst().orElse("version not reported");
                tools.put(name, new Tool(name, State.AVAILABLE, executable,
                        first.length() > 160 ? first.substring(0, 160) : first));
            } catch (java.io.InterruptedIOException e) {
                break;
            } catch (IOException e) {
                tools.put(name, new Tool(name, State.BROKEN, executable, e.getMessage()));
            }
        }
        Set<String> installed = new LinkedHashSet<>();
        boolean checked = false;
        if (tools.containsKey("rustup") && tools.get("rustup").usable()
                && !cancelled.getAsBoolean()) {
            try {
                String output = command(List.of("rustup", "component", "list", "--installed"), cancelled);
                checked = true;
                for (String line : output.lines().toList()) {
                    String word = line.split("\\s+", 2)[0];
                    for (String name : COMPONENTS) {
                        if (word.equals(name) || word.startsWith(name + "-"))
                            installed.add(name);
                    }
                }
            } catch (java.io.InterruptedIOException ignored) {
                // Cancellation is observed by the worker job.
            } catch (IOException ignored) {
                // Never assert an unverified component set is complete.
            }
        }
        return new Report(tools, installed, checked);
    }

    /**
     * Deterministic explicit-approval plan: install rustup first if truly absent,
     * then a stable toolchain only when compiler/Cargo is broken, then components.
     */
    public static List<String> essentials(Report report, boolean windows) {
        List<String> ids = new ArrayList<>();
        boolean rustup = report.available("rustup");
        if (!rustup) {
            if (!windows || !report.available("winget")) return List.of();
            if (report.readyForCargo()) return List.of(); // Do not replace externally-managed Rust.
            ids.add("install-rustup");
        }
        if (!report.readyForCargo())
            ids.add("install-stable");
        for (String component : List.of("rust-src", "rust-analyzer", "clippy", "rustfmt")) {
            if (!report.componentListVerified() || !report.componentInstalled(component))
                ids.add("install-" + component);
        }
        return List.copyOf(ids);
    }
}
