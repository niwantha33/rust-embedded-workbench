// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.security.PublicKey;

/** Unforgeable API boundary: only correctly signed metadata can become a release. */
public final class VerifiedRelease {
    private final UpdateManifest manifest;
    private VerifiedRelease(UpdateManifest manifest) {
        this.manifest = manifest;
    }
    public static VerifiedRelease authenticate(byte[] metadata, byte[] signature,
            PublicKey trustedPublisher) throws IOException {
        return new VerifiedRelease(SignedChannel.verify(metadata, signature, trustedPublisher));
    }
    public UpdateManifest manifest() { return manifest; }
}
