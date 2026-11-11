// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;

/** Eclipse-style four-component numeric versions, ordered without signed-int overflow. */
public record UpdateVersion(long major, long minor, long micro, long qualifier)
        implements Comparable<UpdateVersion> {
    public static UpdateVersion parse(String text) throws IOException {
        if (text == null || !text.matches("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,14}"))
            throw new IOException("Invalid numeric workbench version.");
        String[] values = text.split("\\.");
        try {
            return new UpdateVersion(Long.parseLong(values[0]), Long.parseLong(values[1]),
                    Long.parseLong(values[2]), Long.parseLong(values[3]));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid version component.", e);
        }
    }
    public static boolean newer(String candidate, String current, String newestPreviouslySeen)
            throws IOException {
        UpdateVersion v = parse(candidate);
        return v.compareTo(parse(current)) > 0
                && (newestPreviouslySeen == null || newestPreviouslySeen.isBlank()
                    || v.compareTo(parse(newestPreviouslySeen)) >= 0);
    }
    @Override public int compareTo(UpdateVersion other) {
        int v = Long.compare(major, other.major);
        if (v != 0) return v;
        v = Long.compare(minor, other.minor);
        if (v != 0) return v;
        v = Long.compare(micro, other.micro);
        return v != 0 ? v : Long.compare(qualifier, other.qualifier);
    }
}
