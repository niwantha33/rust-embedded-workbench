// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.setup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;

/**
 * Read-only USB vendor hints from Windows PnP. Does not inspect driver
 * signing/versions, infer exact boards, install drivers or access a device.
 */
public final class ProbeScanner {
    private ProbeScanner() {}
    private static final Pattern VID_PID =
            Pattern.compile("(?i)\\bVID_([0-9a-f]{4})&PID_([0-9a-f]{4})\\b");
    private static final int MAX_OUTPUT_BYTES = 131072;

    public record Observation(String vendorHint, String vendorId, String productId) {}

    public static List<Observation> interpret(String deviceIds) {
        if (deviceIds == null || deviceIds.isBlank()) return List.of();
        var found = new ArrayList<Observation>();
        Set<String> seen = new HashSet<>();
        for (String line : deviceIds.lines().limit(2000).toList()) {
            var match = VID_PID.matcher(line);
            if (!match.find()) continue;
            String vid = match.group(1).toUpperCase(Locale.ROOT);
            String pid = match.group(2).toUpperCase(Locale.ROOT);
            String vendor = switch (vid) {
                case "0451" -> "Texas Instruments USB device (XDS110 candidate)";
                case "0483" -> "STMicroelectronics USB device (ST-LINK candidate)";
                case "1366" -> "SEGGER USB device (J-Link candidate)";
                case "2E8A" -> "Raspberry Pi USB device (CMSIS-DAP candidate)";
                case "303A" -> "Espressif native USB device candidate";
                case "10C4" -> "Silicon Labs USB-UART bridge candidate";
                case "0403" -> "FTDI USB-UART bridge candidate";
                default -> null;
            };
            if (vendor != null && seen.add(vid + ":" + pid))
                found.add(new Observation(vendor, vid, pid));
            if (found.size() >= 30) break;
        }
        return List.copyOf(found);
    }

    public static List<Observation> scanWindows(BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        if (!SetupExecutor.isWindows())
            throw new IOException("Read-only USB scan is available on Windows only");
        if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Scan cancelled");
        // Fixed script, no caller-provided expression or arguments. Read-only cmdlet.
        String script = "$ErrorActionPreference='Stop'; "
                + "Get-PnpDevice -PresentOnly | Where-Object { "
                + "$_.InstanceId -match 'VID_[0-9A-Fa-f]{4}' } | "
                + "Select-Object -ExpandProperty InstanceId";
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile",
                "-NonInteractive", "-Command", script);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.getOutputStream().close();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[2048];
                int n;
                while ((n = stream.read(buffer)) >= 0) {
                    synchronized (output) {
                        if (output.size() >= MAX_OUTPUT_BYTES) {
                            process.destroyForcibly();
                            break;
                        }
                        output.write(buffer, 0, Math.min(n, MAX_OUTPUT_BYTES - output.size()));
                    }
                }
            } catch (IOException ignored) {
                // Process exit status decides whether the scan is trustworthy.
            }
        });
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        try {
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean())
                    throw new java.io.InterruptedIOException("USB scan cancelled");
                if (System.nanoTime() >= deadline)
                    throw new IOException("USB enumeration timed out");
            }
            reader.join(1500);
            if (reader.isAlive() || process.exitValue() != 0)
                throw new IOException("Windows PnP USB enumeration unavailable");
            synchronized (output) {
                if (output.size() >= MAX_OUTPUT_BYTES)
                    throw new IOException("USB scan exceeded its allowed output size");
                return interpret(output.toString(StandardCharsets.UTF_8));
            }
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS))
                    process.destroyForcibly();
            }
        }
    }
}
