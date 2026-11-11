// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies the publisher's detached Ed25519 signature before any manifest
 * fields are trusted. The public key MUST be pinned in the installed product;
 * never download a replacement key from the mutable update channel.
 */
public final class SignedChannel {
    private SignedChannel() {}

    public static PublicKey pinnedKey(String x509Base64) throws GeneralSecurityException {
        try {
            byte[] encoded = Base64.getDecoder().decode(x509Base64);
            if (encoded.length < 40 || encoded.length > 100)
                throw new GeneralSecurityException("Invalid update verification key.");
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid update key encoding.", e);
        }
    }

    public static UpdateManifest verify(byte[] manifest, byte[] detachedSignature,
            PublicKey trustedKey) throws IOException {
        if (trustedKey == null || !"EdDSA".equalsIgnoreCase(trustedKey.getAlgorithm())
                && !"Ed25519".equalsIgnoreCase(trustedKey.getAlgorithm()))
            throw new IOException("Publisher verification key is missing or invalid.");
        if (manifest == null || manifest.length < 1 || manifest.length > UpdateManifest.MAX_MANIFEST_BYTES)
            throw new IOException("Update manifest violates transfer bounds.");
        if (detachedSignature == null || detachedSignature.length < 1 || detachedSignature.length > 256)
            throw new IOException("Missing or oversized update signature.");
        try {
            String encoded = new String(detachedSignature, StandardCharsets.US_ASCII).trim();
            byte[] signature = Base64.getDecoder().decode(encoded);
            if (signature.length != 64) throw new IOException("Invalid Ed25519 signature size.");
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(trustedKey);
            verifier.update(manifest);
            if (!verifier.verify(signature))
                throw new IOException("Update publisher signature did not verify.");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Update publisher signature verification failed.", e);
        }
        // Strict UTF-8: replacement characters must never reinterpret signed bytes.
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(manifest)).toString();
        return UpdateManifest.parse(text);
    }
}
