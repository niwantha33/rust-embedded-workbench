// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/** Documentation metadata; not evidence of working hardware support. */
public record Board(String id, String vendor, String board, String mcu,
        String architecture, String rustTarget, Toolchain toolchain,
        String framework, String debugCandidate, Evidence evidence,
        URI upstreamSource, String notes) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern TARGET = Pattern.compile("[a-zA-Z0-9_.-]+");
    public Board {
        if (id == null || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Invalid board id: " + id);
        for (String value : new String[] {vendor, board, mcu, architecture,
                framework, debugCandidate, notes}) {
            if (value == null || value.isBlank())
                throw new IllegalArgumentException("Missing board field: " + id);
        }
        if (rustTarget == null || !TARGET.matcher(rustTarget).matches())
            throw new IllegalArgumentException("Invalid Rust target: " + id);
        Objects.requireNonNull(toolchain, "toolchain");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(upstreamSource, "source");
        if (!"https".equalsIgnoreCase(upstreamSource.getScheme()))
            throw new IllegalArgumentException("Source must be HTTPS: " + id);
    }
    public enum Toolchain { stable, espup }
    public enum Evidence { DOCS_ONLY, BUILD_VERIFIED, FLASH_VERIFIED, DEBUG_VERIFIED }
}
