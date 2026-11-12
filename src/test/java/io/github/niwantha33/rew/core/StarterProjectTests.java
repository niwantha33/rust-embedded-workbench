// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pure Java assertions; do not invoke Cargo or alter an existing project. */
public final class StarterProjectTests {
    private static int assertions;
    private static void verify(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        assertions++;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("rew-starter-test-");
        try {
            verify(StarterProject.validName("hello_rust"), "valid Rust package name");
            for (String invalid : new String[] {"", "../hello", "two words", "Hello",
                    "con", "com1", "éclair", "bad-name"}) {
                verify(!StarterProject.validName(invalid), "reject invalid name: " + invalid);
            }
            var created = StarterProject.create(root, "hello_rust");
            verify(Files.isRegularFile(created.manifest()), "Cargo manifest created");
            verify(Files.readString(created.manifest()).contains("name = \"hello_rust\""),
                    "correct declared package");
            verify(Files.isRegularFile(created.directory().resolve("src/main.rs")),
                    "first Rust source created");
            verify(Files.readString(created.directory().resolve("src/main.rs")).contains("fn main()"),
                    "valid beginner main function");
            Files.writeString(created.directory().resolve("src/main.rs"), "user changes\n");
            boolean blocked = false;
            try { StarterProject.create(root, "hello_rust"); }
            catch (IOException expected) { blocked = true; }
            verify(blocked, "never reuse an existing project directory");
            verify(Files.readString(created.directory().resolve("src/main.rs"))
                    .equals("user changes\n"), "existing project remains unchanged");
            blocked = false;
            try { StarterProject.create(root.resolve("missing"), "another"); }
            catch (IOException expected) { blocked = true; }
            verify(blocked, "must not create arbitrary parent directories");
            blocked = false;
            try { StarterProject.create(root, "../escape"); }
            catch (IllegalArgumentException expected) { blocked = true; }
            verify(blocked && !Files.exists(root.getParent().resolve("escape")),
                    "cannot traverse out of selected parent");
            System.out.println("StarterProjectTests: " + assertions + " checks passed");
        } finally {
            Path project = root.resolve("hello_rust");
            Files.deleteIfExists(project.resolve("src/main.rs"));
            Files.deleteIfExists(project.resolve("src"));
            Files.deleteIfExists(project.resolve("Cargo.toml"));
            Files.deleteIfExists(project);
            Files.deleteIfExists(root);
        }
    }
}
