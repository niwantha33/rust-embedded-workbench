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
                        + "Update channel: " + (UpdateTrust.isProvisioned()
                                ? "signed GitHub release channel" : "public GitHub Eclipse p2 (updates branch)") + "\n"
                        + "Install trust: Eclipse p2 user approval required\n"
                        + "Update authenticity: " + (UpdateTrust.isProvisioned() ? "signed channel" : "public p2; unsigned trust prompts required") + "\n"
                        + "Auto-check: " + store.getBoolean("updates.autoCheck") + "\n"
                        + "Last successful update check: " + store.getString("updates.lastSuccessfulCheck") + "\n"
                        + "No keys, tokens or project files are shown here.\n"
                        + "Current board entries are documentation-only; hardware is not qualified.");
        return null;
    }
}
