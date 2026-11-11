// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import io.github.niwantha33.rew.updates.GitHubUpdateChannel;
import io.github.niwantha33.rew.updates.UpdateManifest;
import io.github.niwantha33.rew.updates.UpdateVersion;
import io.github.niwantha33.rew.updates.VerifiedRelease;
import io.github.niwantha33.rew.updates.VerifiedUpdateStager;

/**
 * Signed release selection + verified p2 staging.
 * No unsigned channel, executable downloaded scripts, silent install or reset.
 */
public final class UpdateController {
    static final Object FAMILY = new Object();
    private static final AtomicReference<Object> OWNER = new AtomicReference<>();
    private UpdateController() {}

    public static void check(boolean manual) {
        if (!UpdateTrust.isProvisioned()) {
            if (manual) MessageDialog.openInformation(shell(), "Rust Workbench updates",
                    "This preview has no trusted publisher key or qualified release channel yet. "
                    + "Automatic updates remain disabled until the signed installer is released. "
                    + "No unsigned fallback is available.");
            return;
        }
        Object owner = new Object();
        if (!OWNER.compareAndSet(null, owner)) return;
        var check = new Job("Check signed Rust Workbench updates") {
            @Override public boolean belongsTo(Object family) { return family == FAMILY; }
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    var plugin = Activator.getDefault();
                    if (plugin == null) return Status.CANCEL_STATUS;
                    var current = plugin.getBundle().getVersion().toString();
                    var prefs = plugin.getPreferenceStore();
                    var channel = new GitHubUpdateChannel(UpdateTrust.publisher());
                    VerifiedRelease release = channel.check(monitor::isCanceled);
                    String available = release.manifest().version();
                    String lastSeen = prefs.getString("updates.highestVerifiedVersion");
                    // A backward channel cannot change the running environment or cached state.
                    if (!lastSeen.isBlank() && UpdateVersion.parse(available)
                            .compareTo(UpdateVersion.parse(lastSeen)) < 0) {
                        throw new IOException("Signed update channel moved backwards; refuse rollback.");
                    }
                    prefs.setValue("updates.highestVerifiedVersion", available);
                    prefs.setValue("updates.lastSuccessfulCheck", Instant.now().toString());
                    if (!UpdateVersion.newer(available, current, lastSeen)) {
                        if (manual) ui(() -> MessageDialog.openInformation(shell(), "Rust Workbench updates",
                                "Installed: " + current + "\nVerified channel: " + available
                                + "\nNo newer signed version is available."));
                        return Status.OK_STATUS;
                    }
                    if (manual || !available.equals(prefs.getString("updates.lastNotified"))) {
                        prefs.setValue("updates.lastNotified", available);
                        ui(() -> notifyAvailable(release));
                    }
                    return Status.OK_STATUS;
                } catch (InterruptedIOException error) {
                    return Status.CANCEL_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    report(error, manual);
                    return Status.CANCEL_STATUS;
                } finally {
                    OWNER.compareAndSet(owner, null);
                }
            }
        };
        check.setUser(manual);
        check.setSystem(!manual);
        check.schedule();
    }

    private static void notifyAvailable(VerifiedRelease verified) {
        if (shell() == null) return;
        var manifest = verified.manifest();
        var display = PlatformUI.getWorkbench().getDisplay();
        Shell notice = new Shell(display, SWT.TOOL | SWT.ON_TOP | SWT.CLOSE);
        notice.setText("Signed Rust Workbench update");
        notice.setLayout(new GridLayout(2, true));
        Label label = new Label(notice, SWT.WRAP);
        label.setText("Installed: " + Activator.getDefault().getBundle().getVersion()
                + "\nAvailable signed release: " + manifest.version()
                + "\n\n" + manifest.notes().substring(0, Math.min(manifest.notes().length(), 1000)));
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        data.widthHint = 470;
        label.setLayoutData(data);
        Button install = new Button(notice, SWT.PUSH);
        install.setText("Install Update...");
        install.addListener(SWT.Selection, event -> { notice.dispose(); install(verified); });
        Button later = new Button(notice, SWT.PUSH);
        later.setText("Later");
        later.addListener(SWT.Selection, event -> notice.dispose());
        notice.pack();
        var bounds = display.getPrimaryMonitor().getClientArea();
        var size = notice.getSize();
        notice.setLocation(bounds.x + bounds.width - size.x - 20,
                bounds.y + bounds.height - size.y - 35);
        notice.open();
    }

    private static boolean busy() {
        if (Job.getJobManager().find(ResourcesPlugin.FAMILY_MANUAL_BUILD).length > 0
                || Job.getJobManager().find(ResourcesPlugin.FAMILY_AUTO_BUILD).length > 0)
            return true;
        for (var launch : DebugPlugin.getDefault().getLaunchManager().getLaunches())
            if (!launch.isTerminated()) return true;
        return false;
    }

    private static void install(VerifiedRelease verified) {
        if (busy()) {
            MessageDialog.openInformation(shell(), "Update postponed",
                    "Finish builds and debugging before installing. Use Rust Embedded > Check for Updates.");
            return;
        }
        Object owner = new Object();
        if (!OWNER.compareAndSet(null, owner)) return;
        AtomicBoolean handedOff = new AtomicBoolean();
        var job = new Job("Verify Rust Workbench " + verified.manifest().version()) {
            @Override public boolean belongsTo(Object family) { return family == FAMILY; }
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    UpdateManifest manifest = verified.manifest();
                    monitor.beginTask("Verify signed p2 update", manifest.artifacts().size());
                    var source = new GitHubUpdateChannel(UpdateTrust.publisher());
                    // Reauthenticate channel before transferring: stale signed metadata is not sufficient.
                    VerifiedRelease fresh = source.check(monitor::isCanceled);
                    if (!fresh.manifest().equals(manifest))
                        throw new IOException("Signed update changed since notification; please check again.");
                    var cache = Activator.getDefault().getStateLocation().toFile().toPath()
                            .resolve("verified-updates");
                    var site = new VerifiedUpdateStager().stage(fresh, cache,
                            file -> source.artifact(fresh, file, monitor::isCanceled),
                            monitor::isCanceled, name -> {
                                monitor.subTask(name);
                                monitor.worked(1);
                            });
                    URI uri = site.toUri();
                    var p2 = ProvisioningUI.getDefaultUI();
                    var manager = (IMetadataRepositoryManager) p2.getSession()
                            .getProvisioningAgent().getService(IMetadataRepositoryManager.SERVICE_NAME);
                    if (manager == null)
                        throw new IOException("Eclipse p2 metadata service is not available.");
                    var repository = manager.loadRepository(uri, monitor);
                    var matches = repository.query(
                            QueryUtil.createIUQuery(UpdateManifest.FEATURE,
                                    Version.create(manifest.version())), monitor).toSet();
                    if (matches.size() != 1)
                        throw new IOException("Verified p2 site does not contain the specified Rust feature/version.");
                    var install = p2.getInstallOperation(List.copyOf(matches), new URI[] {uri});
                    var resolved = install.resolveModal(monitor);
                    if (resolved.matches(IStatus.ERROR | IStatus.CANCEL))
                        throw new IOException("Eclipse rejected the update plan: " + resolved.getMessage());
                    handedOff.set(true);
                    ui(() -> {
                        try {
                            if (busy()) {
                                MessageDialog.openInformation(shell(), "Update staged",
                                        "Update was verified, but a build or debug session is active. "
                                        + "Finish it and check for updates again.");
                            } else {
                                p2.openInstallWizard(List.copyOf(matches), install, null);
                            }
                        } finally {
                            OWNER.compareAndSet(owner, null);
                        }
                    });
                    return Status.OK_STATUS;
                } catch (InterruptedIOException error) {
                    return Status.CANCEL_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    report(error, true);
                    return Status.CANCEL_STATUS;
                } finally {
                    monitor.done();
                    if (!handedOff.get()) OWNER.compareAndSet(owner, null);
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
    private static void ui(Runnable runnable) {
        if (PlatformUI.isWorkbenchRunning()) {
            PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
                if (Activator.getDefault() != null) runnable.run();
            });
        }
    }
    private static void report(Exception error, boolean manual) {
        var plugin = Activator.getDefault();
        String reason = error.getMessage() == null ? "The update check failed." : error.getMessage();
        if (plugin != null) plugin.getLog().log(
                new Status(IStatus.WARNING, Activator.ID, reason, error));
        if (manual) ui(() -> MessageDialog.openError(shell(), "Rust Workbench update", reason));
    }
}
