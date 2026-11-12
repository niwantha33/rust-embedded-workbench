// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.cargo;

import java.nio.file.Path;
import java.util.List;

import io.github.niwantha33.rew.core.BoardRegistry;

/** CI-only actual host Cargo execution, isolated from user projects and hardware. */
public final class CargoIntegrationSmoke {
    public static void main(String[] args) throws Exception {
        CargoProject project = CargoProject.open(
                Path.of("tests/fixtures/cargo_hello/Cargo.toml"));
        BoardRegistry boards = BoardRegistry.load(Path.of("catalog/boards.tsv"));
        for (var operation : List.of(
                CargoPlan.Operation.METADATA,
                CargoPlan.Operation.CHECK,
                CargoPlan.Operation.BUILD,
                CargoPlan.Operation.TEST_HOST)) {
            CargoPlan plan = CargoPlan.create(project, operation, boards, "", false, true);
            var out = new StringBuilder();
            var result = new CargoExecutor(new CargoExecutor.LocalRunner())
                    .run(plan, true, () -> false, out::append);
            if (!result.succeeded()) {
                throw new AssertionError("Real host cargo " + operation + " failed (exit "
                        + result.exitCode() + "): " + out);
            }
            if (result.truncated())
                throw new AssertionError("Unexpected cargo output truncation");
            System.out.println("PASS real Cargo integration: " + operation + " ("
                    + result.elapsedMillis() + " ms)");
        }
    }
}
