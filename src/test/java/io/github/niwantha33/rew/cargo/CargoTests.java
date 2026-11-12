// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.niwantha33.rew.core.BoardRegistry;

public final class CargoTests {
    private static int count;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        count++;
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void reject(Class<? extends Exception> type, Action action, String name)
            throws Exception {
        try {
            action.run();
        } catch (Exception e) {
            check(type.isInstance(e), name + ": wrong exception " + e);
            return;
        }
        throw new AssertionError("Accepted unsafe Cargo operation: " + name);
    }

    public static void main(String[] args) throws Exception {
        BoardRegistry registry = BoardRegistry.load(Path.of("catalog/boards.tsv"));
        Path directory = Files.createTempDirectory("rew-cargo-test-");
        try {
            Path manifest = directory.resolve("Cargo.toml");
            Files.writeString(manifest, "[package]\nname = \"rew_test\"\nversion = \"0.1.0\"\nedition = \"2024\"\n");
            CargoProject project = CargoProject.open(manifest);
            Path rust = directory.resolve("src");
            Files.createDirectories(rust);
            Path source = rust.resolve("lib.rs");
            Files.writeString(source, "fn main() {}\n");
            String evidence = "src/lib.rs:1:3: error[E0425]: unknown Rust symbol\n"
                    + "src/lib.rs:1:7: warning: unused import\n"
                    + "../other.rs:1:2: error: forged path\n"
                    + "error: other diagnostics stay in Console\n";
            var parsed = CargoDiagnostics.parse(project, evidence);
            check(parsed.size() == 2, "Only proven project-local rustc diagnostics accepted");
            check(parsed.get(0).severity() == CargoDiagnostics.Severity.ERROR
                    && parsed.get(0).line() == 1, "Typed rustc error location");
            check(parsed.get(1).severity() == CargoDiagnostics.Severity.WARNING,
                    "Typed rustc warning location");
            check(CargoDiagnostics.parse(project, "error: no file evidence").isEmpty(),
                    "Unknown Rust diagnostic layout must not invent file markers");
            check(project.directory().equals(directory.toRealPath()), "Validated workspace root");
            check(!project.hasLockfile(), "New Cargo workspace has no lockfile");
            for (var board : registry.all()) {
                for (var mode : List.of(CargoPlan.Operation.CHECK, CargoPlan.Operation.BUILD,
                        CargoPlan.Operation.CLIPPY)) {
                    CargoPlan plan = CargoPlan.create(project, mode, registry,
                            board.id(), false, true);
                    check(plan.command().get(0).equals("cargo"), "Only cargo executable");
                    check(plan.command().containsAll(List.of("--target", board.rustTarget())),
                            "Catalog Rust target preserved: " + board.id());
                    check(plan.command().contains("--offline"), "Optional offline mode is effective");
                    check(!plan.command().contains("--locked"), "No nonexistent lockfile");
                    check(plan.mayExecuteProjectCode(), "Build/check may execute external code");
                    check(plan.safetyNotice().contains("build scripts"), "Informed consent warning");
                }
            }
            var hostTest = CargoPlan.create(project, CargoPlan.Operation.TEST_HOST,
                    registry, "esp32s3-devkit", true, false);
            check(!hostTest.command().contains("--target"), "Tests are host-only");
            check(hostTest.command().contains("--release"), "Explicit release profile");
            check(hostTest.safetyNotice().contains("never on the MCU"), "Host test clearly labelled");
            var metadata = CargoPlan.create(project, CargoPlan.Operation.METADATA,
                    registry, "", false, true);
            check(metadata.command().containsAll(List.of("metadata", "--no-deps",
                    "--format-version", "1")), "Metadata no-deps and stable format");
            check(!metadata.mayExecuteProjectCode(), "Metadata command not labelled as a build");
            var clean = CargoPlan.create(project, CargoPlan.Operation.CLEAN,
                    registry, "", false, false);
            check(clean.command().get(1).equals("clean"), "Clean is explicit");
            check(clean.safetyNotice().contains("removes"), "Clean warning");
            reject(java.util.NoSuchElementException.class,
                    () -> CargoPlan.create(project, CargoPlan.Operation.BUILD, registry,
                            "malicious;rm -rf", false, false), "Arbitrary board rejected");
            Path other = directory.resolve("evil.toml");
            Files.writeString(other, "evil");
            reject(IOException.class, () -> CargoProject.open(other),
                    "Only Cargo.toml is allowed");
            reject(IOException.class, () -> CargoProject.open(directory.resolve("missing/Cargo.toml")),
                    "Missing manifest denied");
            Path toolFolder = directory.resolve("shims");
            Files.createDirectories(toolFolder);
            String exe = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "cargo.exe" : "cargo";
            Path shim = toolFolder.resolve(exe);
            Files.writeString(shim, "synthetic shim");
            shim.toFile().setExecutable(true);
            if (Files.isExecutable(shim)) {
                Path picked = CargoToolResolver.discover(toolFolder.toString(),
                        directory.resolve("no-home-cargo"), exe.endsWith(".exe"));
                check(picked.equals(shim.toAbsolutePath().normalize()),
                        "Cargo launcher path must retain its Cargo basename");
            } else {
                System.out.println("SKIP synthetic executable lookup: temp mount disallows execution");
            }
            if (!exe.endsWith(".exe")) {
                try {
                    Path rustup = toolFolder.resolve("rustup");
                    Files.writeString(rustup, "rustup");
                    rustup.toFile().setExecutable(true);
                    shim.toFile().delete();
                    Files.createSymbolicLink(shim, rustup);
                    if (Files.isExecutable(shim)) {
                        Path kept = CargoToolResolver.discover(toolFolder.toString(),
                                directory.resolve("no-home-cargo"), false);
                        check(kept.equals(shim.toAbsolutePath().normalize()),
                                "Rustup Cargo symlink must not resolve to a rustup argv[0]");
                    }
                } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
                    // Symbolic links may be unavailable on restricted CI users.
                }
            }
            Files.writeString(directory.resolve("Cargo.lock"), "version = 4\n");
            check(CargoProject.open(manifest).hasLockfile(), "Lockfile detection");
            var locked = CargoPlan.create(project, CargoPlan.Operation.BUILD,
                    registry, "", false, false);
            check(locked.command().contains("--locked"), "Existing lockfile must be preserved");

            var called = new AtomicBoolean();
            var executor = new CargoExecutor((plan, cancelled, output) -> {
                called.set(true);
                output.accept("test cargo output");
                return new CargoExecutor.Result(0, false, 1, "test cargo output");
            });
            reject(SecurityException.class, () -> executor.run(locked, false,
                    () -> false, text -> {}), "Cargo execution without consent");
            check(!called.get(), "Consent gate must not invoke runner");
            reject(InterruptedIOException.class, () -> executor.run(locked, true,
                    () -> true, text -> {}), "Already cancelled command");
            check(!called.get(), "Cancelled command must not invoke runner");
            var result = executor.run(locked, true, () -> false, text -> {});
            check(result.succeeded() && called.get(), "Approved Cargo command dispatched");
            check(locked.command().contains(manifest.toRealPath().toString()),
                    "Manifest path is an exact argument, never interpolated in a shell");
        } finally {
            try (var stream = Files.walk(directory)) {
                for (Path file : stream.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(file);
            }
        }
        System.out.println("PASS Cargo core safety: " + count + " checks");
    }
}
