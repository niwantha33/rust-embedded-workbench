// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Immutable, bounded update-site metadata. Parsing never authorises installation. */
public record UpdateManifest(String version, String revision, String directory,
        String notes, List<Artifact> artifacts) {
    public static final String FEATURE = "io.github.niwantha33.rew.feature.feature.group";
    public static final int MAX_MANIFEST_BYTES = 65_536;
    public static final int MAX_FILE_BYTES = 8_388_608;
    public static final long MAX_TOTAL_BYTES = 67_108_864;
    public record Artifact(String path, int size, String sha256) {}

    public UpdateManifest {
        artifacts = List.copyOf(artifacts);
    }

    public static UpdateManifest parse(String document) throws IOException {
        if (document == null || document.length() > MAX_MANIFEST_BYTES) {
            throw new IOException("Missing or oversized update manifest.");
        }
        var properties = new Properties();
        try {
            properties.load(new StringReader(document));
        } catch (RuntimeException ex) {
            throw new IOException("Malformed update metadata.", ex);
        }
        if (!"1".equals(properties.getProperty("schema"))
                || !FEATURE.equals(properties.getProperty("feature"))) {
            throw new IOException("Update channel is not for this workbench.");
        }
        String version = require(properties, "version");
        if (!version.matches("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,14}"))
            throw new IOException("Invalid release version.");
        String revision = require(properties, "revision");
        if (!revision.matches("[0-9a-f]{40}"))
            throw new IOException("Release must reference an immutable commit.");
        String directory = require(properties, "directory");
        if (!directory.equals("releases/" + version))
            throw new IOException("Unrecognised release directory.");
        int count;
        try {
            count = Integer.parseInt(require(properties, "files"));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid file count.", e);
        }
        if (count < 3 || count > 100) throw new IOException("File count outside allowed bounds.");
        var artifacts = new ArrayList<Artifact>();
        Set<String> paths = new HashSet<>();
        long total = 0;
        for (int i = 0; i < count; i++) {
            String prefix = "file." + i + ".";
            String path = require(properties, prefix + "path");
            if (!allowedArtifact(path) || !paths.add(path))
                throw new IOException("Invalid or duplicate p2 file path.");
            int size;
            try {
                size = Integer.parseInt(require(properties, prefix + "size"));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid artifact size.", e);
            }
            String digest = require(properties, prefix + "sha256");
            if (size < 1 || size > MAX_FILE_BYTES || !digest.matches("[0-9a-f]{64}"))
                throw new IOException("Invalid artifact integrity record.");
            total += size;
            if (total > MAX_TOTAL_BYTES) throw new IOException("Update exceeds size budget.");
            artifacts.add(new Artifact(path, size, digest));
        }
        if (!paths.contains("p2.index")
                || (!paths.contains("content.jar") && !paths.contains("content.xml"))
                || (!paths.contains("artifacts.jar") && !paths.contains("artifacts.xml")))
            throw new IOException("Incomplete Eclipse p2 repository.");
        String notes = properties.getProperty("notes", "Rust Embedded Workbench update.");
        if (notes.length() > 2048 || notes.chars().anyMatch(c -> c < 32 && c != '\t'))
            throw new IOException("Invalid release notes.");
        return new UpdateManifest(version, revision, directory, notes, artifacts);
    }

    private static boolean allowedArtifact(String path) {
        return path.equals("p2.index")
                || path.matches("(?:content|artifacts)\\.(?:jar|xml|xml\\.xz)")
                || path.matches("(?:plugins|features)/[A-Za-z0-9_.-]+\\.jar");
    }

    private static String require(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing update field: " + key);
        return value;
    }
}
