// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolve Cargo to an absolute executable, avoiding project-local executable lookup. */
public final class CargoToolResolver {
    private CargoToolResolver() {}

    public static Path discover(String pathEnv, Path home, boolean windows) throws IOException {
        List<Path> folders = new ArrayList<>();
        if (home != null) folders.add(home.resolve(".cargo").resolve("bin"));
        if (pathEnv != null) {
            for (String element : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (element.isBlank()) continue;
                try {
                    Path directory = Path.of(element);
                    if (directory.isAbsolute()) folders.add(directory);
                } catch (RuntimeException ignored) {
                    // Invalid PATH entries cannot become shell instructions.
                }
            }
        }
        String executable = windows ? "cargo.exe" : "cargo";
        for (Path folder : folders) {
            Path candidate = folder.resolve(executable);
            if (!Files.isRegularFile(candidate) || !Files.isExecutable(candidate))
                continue;
            // Important: rustup dispatches cargo/rustc by argv[0].
            // Do NOT canonicalise the launcher to the underlying rustup executable.
            // Keep the absolute cargo shim path, including its final basename.
            return candidate.toAbsolutePath().normalize();
        }
        throw new IOException("Cargo executable not found. Open Rust Embedded > Setup & Drivers "
                + "to install Rust, then restart Eclipse to refresh PATH.");
    }

    public static Path current() throws IOException {
        return discover(System.getenv("PATH"),
                Path.of(System.getProperty("user.home")),
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                        .contains("win"));
    }
}
