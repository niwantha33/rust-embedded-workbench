// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IStartup;

/** Nonblocking automatic update checks, never automatic installation or restart. */
public final class UpdateStartup implements IStartup {
    @Override public void earlyStartup() {
        var scheduler = new Job("Rust Embedded Workbench update scheduler") {
            @Override public boolean belongsTo(Object family) {
                return family == UpdateController.FAMILY;
            }
            @Override protected IStatus run(IProgressMonitor monitor) {
                var plugin = Activator.getDefault();
                if (monitor.isCanceled() || plugin == null) return Status.CANCEL_STATUS;
                if (UpdateTrust.isProvisioned()
                        && plugin.getPreferenceStore().getBoolean("updates.autoCheck")) {
                    UpdateController.check(false);
                }
                if (!monitor.isCanceled() && Activator.getDefault() != null) {
                    schedule(6L * 60 * 60 * 1000);
                }
                return Status.OK_STATUS;
            }
        };
        scheduler.setSystem(true);
        scheduler.schedule(20_000);
    }
}
