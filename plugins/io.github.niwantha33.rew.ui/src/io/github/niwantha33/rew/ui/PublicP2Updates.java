// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * Public, credential-free Eclipse p2 update track. The site is a fixed
 * HTTPS public repository; native Eclipse p2 retains its artifact/authority
 * trust prompts. Never permits silent installation or arbitrary URLs.
 *
 * This deliberately does NOT claim publisher-authenticated artifacts:
 * unsigned JARs remain untrusted and require the native Eclipse dialog.
 */
final class PublicP2Updates {
    private static final String FEATURE =
            "io.github.niwantha33.rew.feature.feature.group";
    static final URI SITE = URI.create(
            "https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench/updates/site/");
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private PublicP2Updates() {}

    static void check(boolean manual) {
        if (!BUSY.compareAndSet(false, true)) return;
        Job job = new Job("Check public Rust Embedded Workbench p2 updates") {
            @Override public boolean belongsTo(Object family) {
                return family == UpdateController.FAMILY;
            }
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    var agent = ProvisioningUI.getDefaultUI().getSession().getProvisioningAgent();
                    var metadata = (IMetadataRepositoryManager) agent.getService(
                            IMetadataRepositoryManager.SERVICE_NAME);
                    var artifacts = (IArtifactRepositoryManager) agent.getService(
                            IArtifactRepositoryManager.SERVICE_NAME);
                    if (metadata == null || artifacts == null)
                        throw new IllegalStateException("Eclipse p2 repositories are unavailable.");
                    // Persist the fixed repository for Eclipse's native scheduled updates.
                    metadata.addRepository(SITE);
                    artifacts.addRepository(SITE);
                    var repository = metadata.loadRepository(SITE, monitor);
                    var releases = repository.query(QueryUtil.createIUQuery(FEATURE),
                            monitor).toSet();
                    var newest = releases.stream()
                            .max(Comparator.comparing(IInstallableUnit::getVersion));
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    if (newest.isEmpty())
                        throw new IllegalStateException("The public update site has no Rust Workbench feature.");
                    Version installed = Version.create(Activator.getDefault().getBundle()
                            .getVersion().toString());
                    Version available = newest.get().getVersion();
                    var preferences = Activator.getDefault().getPreferenceStore();
                    preferences.setValue("updates.lastSuccessfulCheck", Instant.now().toString());
                    if (available.compareTo(installed) <= 0) {
                        if (manual) onUi(() -> MessageDialog.openInformation(shell(),
                                "Rust Embedded Workbench updates",
                                "Installed: " + installed + "\nPublic p2 site: " + SITE
                                + "\nLatest published: " + available
                                + "\nNo newer Rust Workbench update is available."));
                        return Status.OK_STATUS;
                    }
                    if (manual || !available.toString().equals(
                            preferences.getString("updates.lastNotified"))) {
                        preferences.setValue("updates.lastNotified", available.toString());
                        onUi(() -> {
                            if (MessageDialog.openQuestion(shell(),
                                    "Rust Embedded Workbench update available",
                                    "A newer public Eclipse p2 update is available.\n"
                                    + "Installed: " + installed + "\nAvailable: " + available
                                    + "\nSource: " + SITE + "\n\n"
                                    + "This preview has no custom publisher signature. "
                                    + "Eclipse will review package trust, licences and unsigned "
                                    + "artifacts before installation.\n\n"
                                    + "Review the update in Eclipse now?")) {
                                install();
                            }
                        });
                    }
                    return Status.OK_STATUS;
                } catch (Exception error) {
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    String message = error.getMessage() == null
                            ? "Could not read the public p2 update site."
                            : error.getMessage();
                    var plugin = Activator.getDefault();
                    if (plugin != null) plugin.getLog().log(
                            new Status(IStatus.WARNING, Activator.ID,
                                    "Public p2 update check failed: " + message, error));
                    if (manual) onUi(() -> MessageDialog.openInformation(shell(),
                            "Rust Embedded Workbench updates",
                            "The public update site is not available yet or cannot be read.\n"
                            + SITE + "\n\n" + message
                            + "\n\nNo software was installed."));
                    return new Status(IStatus.WARNING, Activator.ID,
                            "Public p2 update check failed: " + message);
                } finally {
                    BUSY.set(false);
                }
            }
        };
        job.setSystem(!manual);
        job.setUser(manual);
        job.schedule();
    }

    private static boolean isFirmwareBusy() {
        if (Job.getJobManager().find(ResourcesPlugin.FAMILY_MANUAL_BUILD).length > 0
                || Job.getJobManager().find(ResourcesPlugin.FAMILY_AUTO_BUILD).length > 0)
            return true;
        for (var launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
            if (!launch.isTerminated()) return true;
        }
        return false;
    }

    private static void install() {
        if (isFirmwareBusy()) {
            MessageDialog.openInformation(shell(), "Update postponed",
                    "Finish active firmware builds and debug sessions first. "
                    + "Use Rust Embedded > Check for Updates afterwards.");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;
        var job = new Job("Prepare native Eclipse p2 Rust update") {
            @Override public boolean belongsTo(Object family) {
                return family == UpdateController.FAMILY;
            }
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    var ui = ProvisioningUI.getDefaultUI();
                    // Explicitly limit the update operation to the trusted origin
                    // (TLS transport, NOT a publisher-signature assertion).
                    var operation = ui.getUpdateOperation(null, new URI[]{SITE});
                    IStatus resolution = operation.resolveModal(monitor);
                    if (!resolution.isOK())
                        throw new IllegalStateException("Eclipse could not resolve this update: "
                                + resolution.getMessage());
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    onUi(() -> {
                        if (isFirmwareBusy()) {
                            MessageDialog.openInformation(shell(), "Update postponed",
                                    "A build or debugger became active. Try again when idle.");
                        } else {
                            // Eclipse handles unsigned content warnings, trust,
                            // licences and restart. Never run provision job directly.
                            ui.openUpdateWizard(false, operation, null);
                        }
                    });
                    return Status.OK_STATUS;
                } catch (Exception error) {
                    onUi(() -> MessageDialog.openError(shell(), "Rust p2 update",
                            "Could not open the native Eclipse update wizard:\n"
                            + error.getMessage()));
                    return new Status(IStatus.ERROR, Activator.ID,
                            "Rust p2 update preparation failed", error);
                } finally {
                    BUSY.set(false);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private static Shell shell() {
        if (!PlatformUI.isWorkbenchRunning()) return null;
        var window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        return window == null ? null : window.getShell();
    }

    private static void onUi(Runnable action) {
        if (!PlatformUI.isWorkbenchRunning()) return;
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (Activator.getDefault() != null && shell() != null
                    && !shell().isDisposed()) action.run();
        });
    }
}
