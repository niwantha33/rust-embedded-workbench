// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpdateSecurityTests {
    private UpdateSecurityTests() {}
    private static int passes;
    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
        passes++;
    }
    private interface Task { void run() throws Exception; }
    private static void mustReject(Class<? extends Exception> kind, Task task, String description)
            throws Exception {
        try {
            task.run();
        } catch (Exception e) {
            check(kind.isInstance(e), description + " (unexpected: " + e + ")");
            return;
        }
        throw new AssertionError("Accepted invalid input: " + description);
    }

    private static String manifest() {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        String digest = VerifiedUpdateStager.sha256(data);
        StringBuilder properties = new StringBuilder("schema=1\nfeature=" + UpdateManifest.FEATURE
                + "\nversion=0.1.0.20261111220000\nrevision=" + "a".repeat(40)
                + "\ndirectory=releases/0.1.0.20261111220000\nnotes=Test update only\nfiles=3\n");
        String[] paths = {"p2.index", "content.jar", "artifacts.jar"};
        for (int i = 0; i < paths.length; i++) {
            properties.append("file.").append(i).append(".path=").append(paths[i]).append('\n')
                    .append("file.").append(i).append(".size=3\n")
                    .append("file.").append(i).append(".sha256=").append(digest).append('\n');
        }
        return properties.toString();
    }

    private static byte[] sign(byte[] data, java.security.PrivateKey key) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(data);
        return Base64.getEncoder().encode(signer.sign());
    }

    public static void main(String[] args) throws Exception {
        String valid = manifest();
        UpdateManifest m = UpdateManifest.parse(valid);
        check(m.artifacts().size() == 3 && m.version().equals("0.1.0.20261111220000"),
                "valid bounded p2 manifest");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("schema=1", "schema=9")),
                "unsupported schema rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace(UpdateManifest.FEATURE, "alien.feature")),
                "foreign feature rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("revision=" + "a".repeat(40), "revision=main")),
                "moving release reference rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("file.0.path=p2.index", "file.0.path=../p2.index")),
                "traversal rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("file.0.path=p2.index", "file.0.path=evil.exe")),
                "executable rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("file.1.path=content.jar", "file.1.path=p2.index")),
                "duplicate artifact rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("file.1.size=3", "file.1.size=99999999")),
                "oversized artifact rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("files=3", "files=201")),
                "oversized file list rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse(valid.replace("version=0.1.0.20261111220000", "version=0.1.0.1/../../evil")),
                "version path injection rejected");
        mustReject(IOException.class, () -> UpdateManifest.parse("x".repeat(65537)), "manifest size rejected");

        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] metadata = valid.getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(metadata, pair.getPrivate());
        UpdateManifest signed = SignedChannel.verify(metadata, signature, pair.getPublic());
        check(signed.equals(m), "authentic publisher signature verified");
        byte[] wrong = metadata.clone();
        wrong[15] ^= 1;
        mustReject(IOException.class, () -> SignedChannel.verify(wrong, signature, pair.getPublic()),
                "tampered metadata rejected before parsing");
        var other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        mustReject(IOException.class, () -> SignedChannel.verify(metadata, signature, other.getPublic()),
                "unknown publisher rejected");
        mustReject(IOException.class, () -> SignedChannel.verify(metadata,
                "bad".getBytes(StandardCharsets.US_ASCII), pair.getPublic()), "corrupted signature rejected");
        mustReject(IOException.class, () -> SignedChannel.verify(metadata, signature, null),
                "missing trust anchor rejected");

        String encoded = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        check(SignedChannel.pinnedKey(encoded).equals(pair.getPublic()), "pinned key decoded");
        mustReject(java.security.GeneralSecurityException.class,
                () -> SignedChannel.pinnedKey("bad@@"), "untrusted key syntax rejected");

        check(UpdateVersion.newer("0.1.0.20", "0.1.0.10", ""), "newer release");
        check(!UpdateVersion.newer("0.1.0.5", "0.1.0.10", ""), "downgrade denied");
        check(!UpdateVersion.newer("0.1.0.11", "0.1.0.10", "0.1.0.15"),
                "previously seen higher version cannot be silently rolled back");
        check(UpdateVersion.newer("0.1.0.15", "0.1.0.10", "0.1.0.15"),
                "equal to last-seen version allowed");
        mustReject(IOException.class, () -> UpdateVersion.newer("0.1.0.preview", "0.1.0.10", ""),
                "unqualified version rejected");

        BoundedUpdateHttp.requireTrustedOrigin(URI.create(
                "https://api.github.com/repos/niwantha33/rust-embedded-workbench/contents/channel.properties?ref=updates"));
        check(true, "fixed GitHub HTTPS origin accepted");
        for (String candidate : new String[]{
                "http://api.github.com/repos/niwantha33/rust-embedded-workbench/contents/channel.properties",
                "https://evil.example/repos/niwantha33/rust-embedded-workbench/contents/foo",
                "https://api.github.com.evil.example/repos/niwantha33/rust-embedded-workbench/contents/foo",
                "https://user:pw@api.github.com/repos/niwantha33/rust-embedded-workbench/contents/foo",
                "https://api.github.com:443/repos/niwantha33/rust-embedded-workbench/contents/foo",
                "https://api.github.com/repos/niwantha33/zephyr-eclipse-workbench/contents/foo"}) {
            mustReject(IOException.class,
                    () -> BoundedUpdateHttp.requireTrustedOrigin(URI.create(candidate)),
                    "untrusted origin rejected: " + candidate);
        }

        Path cache = Files.createTempDirectory("rew-secure-cache-");
        try {
            var stager = new VerifiedUpdateStager();
            var progress = new AtomicInteger();
            Path ready = stager.stage(m, cache, a -> "abc".getBytes(StandardCharsets.UTF_8),
                    () -> false, s -> progress.incrementAndGet());
            check(Files.isRegularFile(ready.resolve("p2.index")) && progress.get() == 3,
                    "verified p2 site staged with progress");
            mustReject(IOException.class, () -> stager.stage(m, cache,
                    a -> "xyz".getBytes(StandardCharsets.UTF_8),
                    () -> false, s -> {}), "tampered artifact rejected");
            mustReject(IOException.class, () -> stager.stage(m, cache,
                    a -> new byte[0], () -> false, s -> {}), "truncated artifact rejected");
            mustReject(InterruptedIOException.class, () -> stager.stage(m, cache,
                    a -> "abc".getBytes(StandardCharsets.UTF_8),
                    () -> true, s -> {}), "cancelled transfer rejected");
            try (var listing = Files.list(cache)) {
                check(listing.filter(p -> p.getFileName().toString().startsWith(".rew-download-"))
                        .findAny().isEmpty(), "partial downloads cleaned up");
            }
        } finally {
            try (var files = Files.walk(cache)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS update security: " + passes + " checks");
    }
}
