// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Validated existing Cargo manifest; source files are never generated or overwritten. */
public record CargoProject(Path manifest, Path directory) {
    public static CargoProject open(Path selected) throws IOException {
        if (selected == null) throw new IOException("Select an existing Cargo.toml.");
        Path path = selected.toAbsolutePath().normalize();
        if (!"Cargo.toml".equals(path.getFileName().toString()))
            throw new IOException("Selected file must be named Cargo.toml.");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Cargo.toml is missing or points through a symlink.");
        long size = Files.size(path);
        if (size < 1 || size > 1_048_576)
            throw new IOException("Cargo manifest is empty or exceeds the safety size limit.");
        Path directory = path.toRealPath().getParent();
        if (directory == null) throw new IOException("Cargo project has no parent directory.");
        if (Files.isSymbolicLink(directory))
            throw new IOException("Cargo project directory is a symlink.");
        return new CargoProject(path.toRealPath(), directory);
    }

    public boolean hasLockfile() {
        return Files.isRegularFile(directory.resolve("Cargo.lock"), LinkOption.NOFOLLOW_LINKS);
    }
}
