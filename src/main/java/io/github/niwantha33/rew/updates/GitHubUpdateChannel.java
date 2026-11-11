// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Public, immutable-versioned update transport. The channel MUST be signed
 * and the publisher verification key supplied by the installed product.
 * No credentials, project files, or arbitrary URL inputs are accepted.
 */
public final class GitHubUpdateChannel {
    private static final String CONTENTS =
            "https://api.github.com/repos/niwantha33/rust-embedded-workbench/contents/";
    private final BoundedUpdateHttp http;
    private final PublicKey trustedPublisher;
    public GitHubUpdateChannel(PublicKey trustedPublisher) {
        this(new BoundedUpdateHttp(), trustedPublisher);
    }
    GitHubUpdateChannel(BoundedUpdateHttp http, PublicKey trustedPublisher) {
        if (trustedPublisher == null) throw new IllegalArgumentException("No pinned publisher key.");
        this.http = http;
        this.trustedPublisher = trustedPublisher;
    }
    public UpdateManifest check(BooleanSupplier cancelled) throws IOException, InterruptedException {
        byte[] metadata = fetch("channel.properties", "updates", UpdateManifest.MAX_MANIFEST_BYTES, cancelled);
        byte[] signature = fetch("channel.sig", "updates", 256, cancelled);
        return SignedChannel.verify(metadata, signature, trustedPublisher);
    }
    public byte[] artifact(UpdateManifest manifest, UpdateManifest.Artifact artifact,
            BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (!manifest.artifacts().contains(artifact))
            throw new IOException("Artifact is not part of the verified release.");
        // The caller only receives manifest objects after signature verification.
        return fetch(manifest.directory() + "/" + artifact.path(),
                manifest.revision(), artifact.size(), cancelled);
    }
    private byte[] fetch(String path, String revision, int limit,
            BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (!path.matches("[A-Za-z0-9_.\\/-]+") || path.contains("..")
                || !(revision.equals("updates") || revision.matches("[a-f0-9]{40}")))
            throw new IOException("Unsafe channel request.");
        URI uri = URI.create(CONTENTS + path + "?ref="
                + URLEncoder.encode(revision, StandardCharsets.UTF_8));
        return http.get(uri, limit, Duration.ofSeconds(45), cancelled);
    }
}
