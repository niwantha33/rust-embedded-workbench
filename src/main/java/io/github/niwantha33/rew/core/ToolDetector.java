// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Only checks PATH presence; does not execute tools or validate versions. */
public final class ToolDetector {
    private ToolDetector() { }
    public static boolean isOnPath(String program, String path, boolean windows) {
        if (program == null || !program.matches("[a-zA-Z][a-zA-Z0-9-]*") || path == null)
            return false;
        List<String> suffixes = windows ? List.of(".exe", ".cmd", ".bat", "") : List.of("");
        for (String directory : path.split(Pattern.quote(windows ? ";" : ":"), -1)) {
            if (directory.isBlank()) continue;
            for (String suffix : suffixes) {
                try {
                    Path candidate = Path.of(directory).resolve(program + suffix);
                    if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate)))
                        return true;
                } catch (RuntimeException ignored) {
                    // Malformed PATH entry is not evidence of a usable tool.
                }
            }
        }
        return false;
    }
    public static boolean isOnCurrentPath(String tool) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return isOnPath(tool, System.getenv("PATH"), windows);
    }
}
