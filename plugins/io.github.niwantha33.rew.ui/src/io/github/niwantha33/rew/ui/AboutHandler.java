// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

public final class AboutHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) {
        var plugin = Activator.getDefault();
        var store = plugin.getPreferenceStore();
        MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "About Rust Embedded Workbench",
                "Rust Embedded Workbench (development preview)\n"
                        + "Installed bundle: " + plugin.getBundle().getVersion() + "\n"
                        + "Update channel: public GitHub, signed releases only\n"
                        + "Publisher key: " + (UpdateTrust.isProvisioned() ? "configured" : "not yet provisioned") + "\n"
                        + "Auto-check: " + store.getBoolean("updates.autoCheck") + "\n"
                        + "Last verified check: " + store.getString("updates.lastSuccessfulCheck") + "\n"
                        + "No keys, tokens or project files are shown here.\n"
                        + "Current board entries are documentation-only; hardware is not qualified.");
        return null;
    }
}
