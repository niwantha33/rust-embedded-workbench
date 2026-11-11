// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PlatformUI;

public final class OpenSetupHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            var window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            window.getActivePage().showView(SetupCenterView.ID);
        } catch (Exception error) {
            throw new ExecutionException("Could not open Rust Setup & Drivers.", error);
        }
        return null;
    }
}
