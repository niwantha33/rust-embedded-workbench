// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evidence-only parser for rustc's short format. Unknown diagnostic layouts
 * remain in the Console, never fabricated into Eclipse Problem markers.
 */
public final class CargoDiagnostics {
    private CargoDiagnostics() {}
    private static final Pattern SHORT = Pattern.compile(
            "^(.+?):(\\d{1,8}):(\\d{1,8}): (error|warning)(?:\\[[A-Za-z0-9]+\\])?: (.{1,2048})$");
    public enum Severity { ERROR, WARNING }
    public record Diagnostic(Path file, int line, int column, Severity severity, String message) {}

    public static List<Diagnostic> parse(CargoProject project, String output) {
        if (project == null || output == null || output.isBlank()) return List.of();
        List<Diagnostic> diagnostics = new ArrayList<>();
        int examined = 0;
        for (String text : output.lines().toList()) {
            if (++examined > 20_000 || diagnostics.size() >= 250) break;
            Matcher match = SHORT.matcher(text.strip());
            if (!match.matches()) continue;
            try {
                int line = Integer.parseInt(match.group(2));
                int col = Integer.parseInt(match.group(3));
                if (line < 1 || col < 1) continue;
                Path file = Path.of(match.group(1));
                if (!file.isAbsolute()) file = project.directory().resolve(file);
                // Real path containment prevents traversal and symlink escape.
                Path actual = file.toRealPath();
                if (!actual.startsWith(project.directory()) || !Files.isRegularFile(actual))
                    continue;
                Severity severity = "error".equals(match.group(4))
                        ? Severity.ERROR : Severity.WARNING;
                diagnostics.add(new Diagnostic(actual, line, col, severity, match.group(5)));
            } catch (IOException | NumberFormatException | java.nio.file.InvalidPathException ignored) {
                // Not supported or not provably project-local.
            }
        }
        return List.copyOf(diagnostics);
    }
}
