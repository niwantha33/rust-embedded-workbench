// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal, host-only learning project; no embedded HAL, target, probe or linker claim.
 * Never overwrites any existing directory or source file.
 */
public final class StarterProject {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,47}");
    private static final Set<String> WINDOWS_RESERVED = Set.of("con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private StarterProject() {}

    public record Created(Path directory, Path manifest) {}

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches()
                && !WINDOWS_RESERVED.contains(name.toLowerCase(Locale.ROOT));
    }

    public static Created create(Path parent, String name) throws IOException {
        if (!validName(name))
            throw new IllegalArgumentException(
                    "Use 1–48 lowercase ASCII letters, numbers and underscores; start with a letter.");
        if (parent == null || !Files.isDirectory(parent))
            throw new IOException("Choose an existing parent folder for your first Rust project.");
        Path directory = parent.toAbsolutePath().normalize().resolve(name);
        // createDirectory is atomic with respect to an existing name. No overwrite or merge.
        Files.createDirectory(directory);
        try {
            Path src = Files.createDirectory(directory.resolve("src"));
            Path manifest = directory.resolve("Cargo.toml");
            Files.writeString(manifest, """
                    [package]
                    name = "%s"
                    version = "0.1.0"
                    edition = "2024"

                    [dependencies]
                    """.formatted(name), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.writeString(src.resolve("main.rs"), """
                    fn main() {
                        println!("Hello from Rust Embedded Workbench!");
                    }
                    """, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return new Created(directory, manifest);
        } catch (IOException | RuntimeException failure) {
            // Delete only files this method created, never overwrite an existing project.
            // If a concurrent writer adds data, nonempty directories are left in place.
            try { Files.deleteIfExists(directory.resolve("src/main.rs")); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            try { Files.deleteIfExists(directory.resolve("Cargo.toml")); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            try { Files.deleteIfExists(directory.resolve("src")); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            try { Files.deleteIfExists(directory); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
