// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public final class UpdateSettingsHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) {
        var shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        new PreferencesDialog(shell).open();
        return null;
    }

    private static final class PreferencesDialog extends Dialog {
        private Button automatic;
        PreferencesDialog(Shell parent) {
            super(parent);
        }
        @Override protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText("Rust Embedded Workbench Updates");
        }
        @Override protected Control createDialogArea(Composite parent) {
            Composite base = (Composite) super.createDialogArea(parent);
            Composite content = new Composite(base, SWT.NONE);
            content.setLayout(new GridLayout(1, false));
            content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            Label introduction = new Label(content, SWT.WRAP);
            introduction.setLayoutData(new GridData(450, SWT.DEFAULT));
            introduction.setText("Public Eclipse p2 channel: raw.githubusercontent.com/niwantha33/rust-embedded-workbench/updates/site/\n"
                    + "An installed workbench checks 20 seconds after startup and every six hours.\n"
                    + "Updates require review and Eclipse's normal trust/licence/restart flow.");
            automatic = new Button(content, SWT.CHECK);
            automatic.setText("Check automatically for public Eclipse p2 updates");
            automatic.setSelection(Activator.getDefault().getPreferenceStore()
                    .getBoolean("updates.autoCheck"));
            Label status = new Label(content, SWT.WRAP);
            status.setLayoutData(new GridData(450, SWT.DEFAULT));
            status.setText(UpdateTrust.isProvisioned()
                    ? "Publisher key: present; authenticated releases are enabled."
                    : "Public p2 preview: no GitHub credentials or custom signing key needed. "
                    + "Unsigned artifacts are untrusted and Eclipse must ask before installing. "
                    + "Updates are never silently installed.");
            return base;
        }
        @Override protected void okPressed() {
            Activator.getDefault().getPreferenceStore().setValue(
                    "updates.autoCheck", automatic.getSelection());
            super.okPressed();
        }
    }
}
