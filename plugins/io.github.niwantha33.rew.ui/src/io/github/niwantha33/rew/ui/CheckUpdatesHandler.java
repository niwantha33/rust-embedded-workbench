// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;

public final class CheckUpdatesHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) {
        UpdateController.check(true);
        return null;
    }
}
