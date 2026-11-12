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
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import io.github.niwantha33.rew.updates.PublicP2Channel;

/**
 * Unsigned public preview: immutable release URL avoids stale mutable p2
 * repository metadata. Eclipse still owns trust, licence and restart prompts.
 * Never silently installs an update or changes another Eclipse feature.
 */
final class PublicP2Updates {
    private static final String FEATURE =
            "io.github.niwantha33.rew.feature.feature.group";
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private PublicP2Updates() {}

    static void check(boolean manual) {
        if (!BUSY.compareAndSet(false, true)) return;
        Job job = new Job("Refresh Rust Embedded Workbench public updates") {
            @Override public boolean belongsTo(Object family) {
                return family == UpdateController.FAMILY;
            }
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    PublicP2Channel.Release release = PublicP2Channel.fetch();
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    // Do not depend on Eclipse's cached metadata from the mutable
                    // /updates/site/ endpoint. Use an immutable per-version URL.
                    var ui = ProvisioningUI.getDefaultUI();
                    var metadata = metadata(ui);
                    var artifacts = artifacts(ui);
                    metadata.addRepository(release.site());
                    artifacts.addRepository(release.site());
                    var repository = metadata.refreshRepository(release.site(), monitor);
                    // Also invalidate cached artifact metadata before p2 resolves.
                    artifacts.refreshRepository(release.site(), monitor);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    var candidates = repository.query(QueryUtil.createIUQuery(FEATURE),
                            monitor).toSet();
                    var newest = candidates.stream()
                            .max(Comparator.comparing(IInstallableUnit::getVersion));
                    if (newest.isEmpty() || !newest.get().getVersion().toString().equals(
                            release.version()))
                        throw new IllegalStateException("Published release metadata does not "
                                + "match the available Eclipse feature version.");
                    Version installed = Version.create(Activator.getDefault()
                            .getBundle().getVersion().toString());
                    Version available = newest.get().getVersion();
                    var preferences = Activator.getDefault().getPreferenceStore();
                    preferences.setValue("updates.lastSuccessfulCheck",
                            Instant.now().toString());
                    if (available.compareTo(installed) < 0)
                        throw new IllegalStateException("Public update channel advertises "
                                + "an older version than this installation; refusing rollback.");
                    if (available.compareTo(installed) == 0) {
                        if (manual) onUi(() -> MessageDialog.openInformation(shell(),
                                "Rust Embedded Workbench updates",
                                "Installed: " + installed
                                + "\nLatest public release: " + available
                                + "\nRepository: " + release.site()
                                + "\nNo newer update is available."));
                        return Status.OK_STATUS;
                    }
                    if (manual || !available.toString().equals(
                            preferences.getString("updates.lastNotified"))) {
                        onUi(() -> {
                            // Only record a notification after the UI actually opened.
                            preferences.setValue("updates.lastNotified", available.toString());
                            if (MessageDialog.openQuestion(shell(),
                                    "Rust Embedded Workbench update available",
                                    "Installed: " + installed + "\nAvailable: " + available
                                    + "\nImmutable release: " + release.site() + "\n\n"
                                    + "This is a public unsigned preview. Eclipse will ask "
                                    + "you to review untrusted code, licences and restart "
                                    + "before installing.\n\n"
                                    + "Review the update in Eclipse now?"))
                                install(release);
                        });
                    }
                    return Status.OK_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    String message = error.getMessage() == null
                            ? "Could not read the public Eclipse p2 repository."
                            : error.getMessage();
                    var plugin = Activator.getDefault();
                    if (plugin != null) plugin.getLog().log(
                            new Status(IStatus.WARNING, Activator.ID,
                                    "Rust public update refresh failed: " + message, error));
                    if (manual) onUi(() -> MessageDialog.openError(shell(),
                            "Rust Embedded Workbench update check failed",
                            "Could not verify a fresh public Eclipse update.\n\n"
                            + message + "\n\nNo software was installed. "
                            + "Try Help > Install New Software with the version-specific "
                            + "site published under the GitHub updates/releases branch."));
                    return new Status(IStatus.WARNING, Activator.ID,
                            "Rust public update refresh failed: " + message, error);
                } finally {
                    BUSY.set(false);
                }
            }
        };
        job.setSystem(!manual);
        job.setUser(manual);
        job.schedule();
    }

    private static IMetadataRepositoryManager metadata(ProvisioningUI ui) {
        var manager = (IMetadataRepositoryManager) ui.getSession()
                .getProvisioningAgent().getService(
                        IMetadataRepositoryManager.SERVICE_NAME);
        if (manager == null) throw new IllegalStateException("Eclipse p2 metadata service missing.");
        return manager;
    }

    private static IArtifactRepositoryManager artifacts(ProvisioningUI ui) {
        var manager = (IArtifactRepositoryManager) ui.getSession()
                .getProvisioningAgent().getService(
                        IArtifactRepositoryManager.SERVICE_NAME);
        if (manager == null) throw new IllegalStateException("Eclipse p2 artifact service missing.");
        return manager;
    }

    private static IInstallableUnit installedFeature(ProvisioningUI ui,
            IProgressMonitor monitor) {
        var agent = ui.getSession().getProvisioningAgent();
        var registry = (IProfileRegistry) agent.getService(IProfileRegistry.SERVICE_NAME);
        if (registry == null)
            throw new IllegalStateException("Eclipse installation profile registry missing.");
        IProfile profile = registry.getProfile(ui.getProfileId());
        if (profile == null)
            throw new IllegalStateException("Cannot identify active Eclipse installation.");
        return profile.query(QueryUtil.createIUQuery(FEATURE), monitor)
                .toSet().stream().max(Comparator.comparing(IInstallableUnit::getVersion))
                .orElseThrow(() -> new IllegalStateException(
                        "Rust Embedded Workbench feature is not installed as a p2 unit."));
    }

    private static boolean isFirmwareBusy() {
        if (Job.getJobManager().find(ResourcesPlugin.FAMILY_MANUAL_BUILD).length > 0
                || Job.getJobManager().find(ResourcesPlugin.FAMILY_AUTO_BUILD).length > 0)
            return true;
        for (var launch : DebugPlugin.getDefault().getLaunchManager().getLaunches())
            if (!launch.isTerminated()) return true;
        return false;
    }

    private static void install(PublicP2Channel.Release release) {
        if (isFirmwareBusy()) {
            MessageDialog.openInformation(shell(), "Update postponed",
                    "Finish active firmware builds and debug sessions first. "
                    + "Use Rust Embedded > Check for Updates afterwards.");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;
        Job job = new Job("Prepare native Eclipse p2 Rust update") {
            @Override public boolean belongsTo(Object family) {
                return family == UpdateController.FAMILY;
            }
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    var ui = ProvisioningUI.getDefaultUI();
                    var manager = metadata(ui);
                    var artifactManager = artifacts(ui);
                    manager.addRepository(release.site());
                    artifactManager.addRepository(release.site());
                    manager.refreshRepository(release.site(), monitor);
                    artifactManager.refreshRepository(release.site(), monitor);
                    // Select only our exact installed feature, not Zephyr or
                    // arbitrary Eclipse roots from the running profile.
                    IInstallableUnit current = installedFeature(ui, monitor);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    if (Version.create(release.version()).compareTo(current.getVersion()) <= 0)
                        throw new IllegalStateException("Rust feature is already current or newer.");
                    var operation = ui.getUpdateOperation(
                            List.of(current), new URI[]{release.site()});
                    IStatus resolution = operation.resolveModal(monitor);
                    if (!resolution.isOK() || operation.getSelectedUpdates().length == 0)
                        throw new IllegalStateException("Eclipse could not resolve a Rust-only "
                                + "upgrade: " + resolution.getMessage());
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    onUi(() -> {
                        if (isFirmwareBusy()) {
                            MessageDialog.openInformation(shell(), "Update postponed",
                                    "Build or debugger is now active. Try again when idle.");
                        } else {
                            // Native Eclipse p2 owns all trust/licence/install/restart UI.
                            ui.openUpdateWizard(false, operation, null);
                        }
                    });
                    return Status.OK_STATUS;
                } catch (Exception error) {
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    onUi(() -> MessageDialog.openError(shell(), "Rust p2 update failed",
                            "The native Eclipse update wizard could not be prepared:\n"
                            + error.getMessage()
                            + "\n\nNo update was installed by the Rust plug-in."));
                    return new Status(IStatus.ERROR, Activator.ID,
                            "Rust-only p2 update resolution failed", error);
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

    private static void onUi(Runnable task) {
        if (!PlatformUI.isWorkbenchRunning()) return;
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            Shell current = shell();
            if (Activator.getDefault() != null && current != null && !current.isDisposed())
                task.run();
        });
    }
}
