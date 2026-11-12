// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.updates;

import java.io.IOException;

/** Synthetic regression: mutable channel metadata only points to immutable, validated sites. */
public final class PublicP2ChannelTests {
    private static int checks;
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
        checks++;
    }
    private static void reject(String metadata) throws Exception {
        try {
            PublicP2Channel.parse(metadata);
            throw new AssertionError("Accepted unsafe public release metadata");
        } catch (IOException expected) {
            checks++;
        }
    }
    public static void main(String[] args) throws Exception {
        String v1 = "0.1.0.20261112001031";
        String v2 = "0.1.0.20261112051358";
        String record = "version=" + v2 + "\nsource=" + "a".repeat(40)
                + "\ndigest=" + "b".repeat(64) + "\n";
        var release = PublicP2Channel.parse(record);
        check(release.version().equals(v2), "Latest channel qualifier parsed");
        check(release.site().toASCIIString().equals(
                "https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench"
                + "/updates/releases/" + v2 + "/"),
                "Exact immutable public p2 URL");
        check(!release.site().toString().endsWith("/updates/site/"),
                "Never reuse the stale mutable p2 metadata path");
        check(PublicP2Channel.parse("version=" + v1 + "\nsource=" + "a".repeat(40)
                + "\ndigest=" + "b".repeat(64)).version().equals(v1),
                "Historical version also resolves immutably");
        reject("version=../etc\nsource=" + "a".repeat(40)
                + "\ndigest=" + "b".repeat(64));
        reject("version=0.1.0.20261112051358/../../../\nsource=" + "a".repeat(40)
                + "\ndigest=" + "b".repeat(64));
        reject("version=0.1.0.20261112051358\nsource=main\ndigest=" + "b".repeat(64));
        reject("version=" + v2 + "\nsource=" + "a".repeat(40)
                + "\ndigest=" + "b".repeat(64) + "\nversion=" + v1);
        reject("version=" + v2 + "\nsource=" + "a".repeat(40)
                + "\ndigest=" + "b".repeat(64) + "\nURL=https://evil.example");
        reject("version=" + v2 + "\nsource=" + "a".repeat(40) + "\ndigest=x");
        reject("version=" + v2);
        reject("x".repeat(4097));
        reject("");
        check("https".equals(PublicP2Channel.CHANNEL.getScheme()),
                "No insecure HTTP channel");
        System.out.println("PASS public p2 channel: " + checks + " security checks");
    }
}
