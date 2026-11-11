// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Stages only manifest-allowlisted p2 artifacts in a new cache directory.
 * Verified bytes never execute, and installation is exclusively Eclipse p2's
 * responsibility once product integration exists.
 */
public final class VerifiedUpdateStager {
    @FunctionalInterface
    public interface Fetcher {
        byte[] fetch(UpdateManifest.Artifact artifact) throws IOException, InterruptedException;
    }
    public Path stage(VerifiedRelease verified, Path cacheRoot, Fetcher fetcher,
            BooleanSupplier cancelled, Consumer<String> progress)
            throws IOException, InterruptedException {
        UpdateManifest manifest = verified.manifest();
        if (Files.isSymbolicLink(cacheRoot))
            throw new IOException("Update cache may not be a symlink.");
        Files.createDirectories(cacheRoot);
        Path root = cacheRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Update cache is not a directory.");
        Path partial = Files.createTempDirectory(root, ".rew-download-");
        boolean finished = false;
        try {
            // Use one fresh private location. No in-place mutation of existing releases.
            for (UpdateManifest.Artifact artifact : manifest.artifacts()) {
                if (cancelled.getAsBoolean()) throw new InterruptedIOException("Update cancelled.");
                byte[] payload = fetcher.fetch(artifact);
                if (payload == null || payload.length != artifact.size()
                        || !sha256(payload).equals(artifact.sha256()))
                    throw new IOException("Update file integrity mismatch: " + artifact.path());
                Path destination = partial.resolve(artifact.path()).normalize();
                if (!destination.startsWith(partial))
                    throw new IOException("Unsafe destination.");
                Files.createDirectories(destination.getParent());
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Duplicate destination.");
                Files.write(destination, payload);
                progress.accept("Verified " + artifact.path());
            }
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("Update cancelled.");
            Path ready = root.resolve("ready-" + manifest.version() + "-" + java.util.UUID.randomUUID());
            try {
                Files.move(partial, ready, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Atomic stage publication is mandatory; never downgrade to unsafe move.
                throw new IOException("Update cache does not support atomic publication.", e);
            }
            finished = true;
            return ready;
        } finally {
            if (!finished && Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) {
                try (var entries = Files.walk(partial)) {
                    for (Path path : entries.sorted(Comparator.reverseOrder()).toList())
                        Files.deleteIfExists(path);
                }
            }
        }
    }
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
