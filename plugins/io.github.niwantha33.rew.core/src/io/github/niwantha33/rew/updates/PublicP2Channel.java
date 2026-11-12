// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Public, unsigned development-channel pointer to immutable p2 releases.
 * This metadata is NOT a publisher signature or proof of code authenticity.
 * Native Eclipse p2 trust and licence prompts remain mandatory.
 */
public final class PublicP2Channel {
    public static final URI CHANNEL = URI.create(
            "https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench"
            + "/updates/public-channel.properties");
    private static final String RELEASES =
            "https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench"
            + "/updates/releases/";
    private static final Pattern VERSION = Pattern.compile(
            "[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{8,14}");
    private static final Pattern SHA = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern DIGEST = Pattern.compile("[a-f0-9]{64}");
    private static final int MAX_BYTES = 4096;

    private PublicP2Channel() {}

    public record Release(String version, String source, String digest, URI site) {}

    public static Release parse(String input) throws IOException {
        if (input == null || input.isBlank() || input.length() > MAX_BYTES)
            throw new IOException("Missing or oversized public update manifest");
        Map<String, String> fields = new HashMap<>();
        for (String line : input.split("\\n", -1)) {
            if (line.isBlank()) continue;
            int position = line.indexOf('=');
            if (position <= 0 || position == line.length() - 1)
                throw new IOException("Malformed public update manifest");
            String key = line.substring(0, position);
            String value = line.substring(position + 1);
            if (!("version".equals(key) || "source".equals(key) || "digest".equals(key))
                    || fields.putIfAbsent(key, value) != null)
                throw new IOException("Unknown or duplicate update manifest field");
        }
        String version = fields.get("version");
        String source = fields.get("source");
        String digest = fields.get("digest");
        if (version == null || !VERSION.matcher(version).matches()
                || source == null || !SHA.matcher(source).matches()
                || digest == null || !DIGEST.matcher(digest).matches())
            throw new IOException("Public update manifest has invalid version or provenance");
        // The URL is derived solely from a strictly numeric validated version.
        URI site = URI.create(RELEASES + version + "/");
        return new Release(version, source, digest, site);
    }

    /** HTTPS only, no redirects, tight network deadlines and bounded response bytes. */
    public static Release fetch() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(CHANNEL)
                .header("Accept", "text/plain")
                .header("Cache-Control", "no-cache")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream stream = response.body()) {
            if (response.statusCode() != 200)
                throw new IOException("Public update channel returned HTTP "
                        + response.statusCode());
            byte[] raw = stream.readNBytes(MAX_BYTES + 1);
            if (raw.length > MAX_BYTES)
                throw new IOException("Public update manifest exceeds size limit");
            return parse(new String(raw, StandardCharsets.US_ASCII));
        }
    }
}
