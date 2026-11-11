// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.BundleContext;

/** Rust Embedded Workbench plug-in lifecycle; independent of Zephyr IDs. */
public final class Activator extends AbstractUIPlugin {
    public static final String ID = "io.github.niwantha33.rew.ui";
    private static Activator instance;

    @Override public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        getPreferenceStore().setDefault("updates.autoCheck", true);
        getPreferenceStore().setDefault("updates.highestVerifiedVersion", "");
        getPreferenceStore().setDefault("updates.lastSuccessfulCheck", "");
        getPreferenceStore().setDefault("updates.lastNotified", "");
    }

    @Override public void stop(BundleContext context) throws Exception {
        Job.getJobManager().cancel(UpdateController.FAMILY);
        instance = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return instance;
    }
}
