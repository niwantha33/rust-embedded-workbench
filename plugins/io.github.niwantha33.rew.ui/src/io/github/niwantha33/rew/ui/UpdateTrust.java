// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

import io.github.niwantha33.rew.updates.SignedChannel;

/** A publisher public key must be packaged in the installed binary, never downloaded. */
final class UpdateTrust {
    private UpdateTrust() {}

    static PublicKey publisher() throws IOException {
        try (var input = UpdateTrust.class.getResourceAsStream("/publisher-key.b64")) {
            if (input == null) {
                throw new IOException("Secure update channel not activated: this preview does not "
                        + "include a qualified publisher public key. No unsigned fallback is allowed.");
            }
            byte[] data = input.readNBytes(4097);
            if (data.length == 4097)
                throw new IOException("Invalid bundled update trust anchor.");
            return SignedChannel.pinnedKey(new String(data, StandardCharsets.US_ASCII).trim());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Bundled update verification key is invalid.", e);
        }
    }

    static boolean isProvisioned() {
        return UpdateTrust.class.getResource("/publisher-key.b64") != null;
    }
}
