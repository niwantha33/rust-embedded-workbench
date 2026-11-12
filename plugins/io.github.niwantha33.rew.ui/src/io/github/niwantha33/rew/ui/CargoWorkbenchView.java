// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.part.ViewPart;

import io.github.niwantha33.rew.cargo.CargoDiagnostics;
import io.github.niwantha33.rew.cargo.CargoExecutor;
import io.github.niwantha33.rew.cargo.CargoPlan;
import io.github.niwantha33.rew.cargo.CargoProject;
import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;
import io.github.niwantha33.rew.setup.HostToolchain;

/**
 * Native Eclipse Cargo control centre. Import links existing sources into a
 * separate workspace project; it never copies, overwrites or generates Rust files.
 * Build/test commands are allowlisted and always require explicit approval.
 */
public final class CargoWorkbenchView extends ViewPart {
    public static final String ID = "io.github.niwantha33.rew.ui.cargoWorkbench";
    private final List<CargoProject> projects = new ArrayList<>();
    private List<Board> boards = List.of();
    private BoardRegistry registry;
    private Combo projectBox;
    private Combo operationBox;
    private Combo targetBox;
    private Button releaseBox;
    private Button offlineBox;
    private Button runButton;
    private Button cancelButton;
    private Label toolStatus;
    private Button verifyButton;
    private volatile HostToolchain.Report tools;
    private final AtomicBoolean toolScan = new AtomicBoolean();
    private Text status;
    private Job currentJob;
    private final AtomicBoolean running = new AtomicBoolean();

    @Override public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(4, false));
        Label description = new Label(parent, SWT.WRAP);
        description.setText("CARGO WORKBENCH — Import or select an existing Rust project. "
                + "Build targets are documentation candidates, not verified MCU firmware.");
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
        toolStatus = new Label(parent, SWT.WRAP);
        toolStatus.setText("Rust toolchain: verifying Cargo, rustc and rustup...");
        toolStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
        new Label(parent, SWT.NONE).setText("Cargo project:");
        projectBox = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        projectBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        Button importButton = new Button(parent, SWT.PUSH);
        importButton.setText("Import Existing...");
        importButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        importButton.addListener(SWT.Selection, event -> importExisting());
        Button refreshButton = new Button(parent, SWT.PUSH);
        refreshButton.setText("Refresh Projects");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        refreshButton.addListener(SWT.Selection, event -> refreshProjects(null));

        new Label(parent, SWT.NONE).setText("Cargo action:");
        operationBox = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        operationBox.setItems(new String[] {
                "Check", "Build", "Clippy", "Test (HOST ONLY)", "Clean", "Metadata (read only)"
        });
        operationBox.select(0);
        operationBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

        new Label(parent, SWT.NONE).setText("Target:");
        targetBox = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        targetBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        targetBox.add("Host (no cross-target)");
        try {
            registry = readCatalog();
            boards = registry.all();
            for (Board board : boards) {
                targetBox.add(board.vendor() + " — " + board.board() + " ("
                        + board.rustTarget() + ")");
            }
        } catch (IOException | IllegalArgumentException e) {
            Activator.getDefault().getLog().log(
                    new Status(IStatus.ERROR, Activator.ID, "Cargo board catalog invalid", e));
        }
        targetBox.select(0);
        operationBox.addListener(SWT.Selection, event -> updatePlan());
        targetBox.addListener(SWT.Selection, event -> updatePlan());
        projectBox.addListener(SWT.Selection, event -> updatePlan());

        releaseBox = new Button(parent, SWT.CHECK);
        releaseBox.setText("Release profile");
        releaseBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        offlineBox = new Button(parent, SWT.CHECK);
        offlineBox.setText("Offline (no network)");
        offlineBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        releaseBox.addListener(SWT.Selection, event -> updatePlan());
        offlineBox.addListener(SWT.Selection, event -> updatePlan());

        runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Run Cargo...");
        runButton.setEnabled(false);
        runButton.addListener(SWT.Selection, event -> runCargo());
        cancelButton = new Button(parent, SWT.PUSH);
        cancelButton.setText("Cancel Running Job");
        cancelButton.setEnabled(false);
        cancelButton.addListener(SWT.Selection, event -> {
            if (currentJob != null) currentJob.cancel();
        });
        Button setupButton = new Button(parent, SWT.PUSH);
        setupButton.setText("Setup Rust & Cargo...");
        setupButton.addListener(SWT.Selection, event -> {
            try {
                getSite().getPage().showView(SetupCenterView.ID);
            } catch (org.eclipse.ui.PartInitException error) {
                MessageDialog.openError(getSite().getShell(), "Cargo tools",
                        "Cannot open Setup & Drivers: " + error.getMessage());
            }
        });
        verifyButton = new Button(parent, SWT.PUSH);
        verifyButton.setText("Verify Tools");
        verifyButton.addListener(SWT.Selection, event -> verifyToolchain());

        status = new Text(parent, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData summary = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
        summary.heightHint = 175;
        status.setLayoutData(summary);
        refreshProjects(null);
        verifyToolchain();
    }

    private static BoardRegistry readCatalog() throws IOException {
        try (InputStream input = Board.class.getResourceAsStream("/catalog/boards.tsv")) {
            if (input == null) throw new IOException("Bundled board catalog missing");
            Path temporary = Files.createTempFile("rew-cargo-boards-", ".tsv");
            try {
                Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return BoardRegistry.load(temporary);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private CargoProject selectedProject() {
        int index = projectBox.getSelectionIndex();
        return index >= 0 && index < projects.size() ? projects.get(index) : null;
    }

    private void refreshProjects(Path selectManifest) {
        if (projectBox == null || projectBox.isDisposed()) return;
        projects.clear();
        projectBox.removeAll();
        int choose = -1;
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            var ordinary = project.getFile("Cargo.toml");
            var linked = project.getFolder("source").getFile("Cargo.toml");
            var file = ordinary.exists() ? ordinary : linked;
            if (!file.exists() || file.getLocation() == null) continue;
            try {
                CargoProject cargo = CargoProject.open(file.getLocation().toFile().toPath());
                projectBox.add(project.getName() + " — " + cargo.directory());
                projects.add(cargo);
                if (selectManifest != null && cargo.manifest().equals(selectManifest))
                    choose = projects.size() - 1;
            } catch (IOException ignored) {
                // Not a valid Cargo project; show only verified readable manifests.
            }
        }
        if (!projects.isEmpty())
            projectBox.select(choose >= 0 ? choose : 0);
        updatePlan();
    }

    private void importExisting() {
        DirectoryDialog picker = new DirectoryDialog(getSite().getShell());
        picker.setText("Select an existing Rust project directory containing Cargo.toml");
        picker.setMessage("Only an Eclipse linked folder is created; existing Rust files are not moved.");
        String selected = picker.open();
        if (selected == null) return;
        try {
            CargoProject cargo = CargoProject.open(Path.of(selected).resolve("Cargo.toml"));
            if (!MessageDialog.openConfirm(getSite().getShell(), "Link existing Cargo project",
                    "Create a new Eclipse project linking to:\n"
                    + cargo.directory() + "\n\n"
                    + "The Rust sources remain in their original directory. "
                    + "Editing or deleting source files in Eclipse affects those original files. "
                    + "No files are copied or overwritten. Continue?"))
                return;
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            String rootName = cargo.directory().getFileName().toString()
                    .replaceAll("[^A-Za-z0-9_-]", "-");
            if (rootName.isBlank()) rootName = "project";
            String basename = "rust-" + rootName;
            String name = basename;
            int next = 2;
            while (workspace.getRoot().getProject(name).exists() && next < 101)
                name = basename + "-" + next++;
            if (workspace.getRoot().getProject(name).exists())
                throw new IOException("Too many imported projects with this name.");
            IProject project = workspace.getRoot().getProject(name);
            // The .project file is in the Eclipse workspace, not the Rust source.
            project.create(null);
            project.open(null);
            IFolder source = project.getFolder("source");
            source.createLink(new org.eclipse.core.runtime.Path(
                    cargo.directory().toString()), IResource.NONE, null);
            refreshProjects(cargo.manifest());
            status.setText("Linked Cargo project into Eclipse as " + name
                    + "/source. Files remain at " + cargo.directory()
                    + "\nEditing/deleting linked source files affects the originals.");
        } catch (Exception error) {
            MessageDialog.openError(getSite().getShell(), "Rust project import",
                    "Could not link the selected Cargo project:\n" + error.getMessage());
        }
    }

    private CargoPlan.Operation operation() {
        return switch (operationBox.getSelectionIndex()) {
            case 0 -> CargoPlan.Operation.CHECK;
            case 1 -> CargoPlan.Operation.BUILD;
            case 2 -> CargoPlan.Operation.CLIPPY;
            case 3 -> CargoPlan.Operation.TEST_HOST;
            case 4 -> CargoPlan.Operation.CLEAN;
            default -> CargoPlan.Operation.METADATA;
        };
    }

    private CargoPlan createPlan() throws IOException {
        CargoProject cargo = selectedProject();
        if (cargo == null) throw new IOException("Import or select a Cargo project first.");
        if (registry == null) throw new IOException("No valid board registry available.");
        int index = targetBox.getSelectionIndex();
        String boardId = index >= 1 && index <= boards.size()
                ? boards.get(index - 1).id() : "";
        return CargoPlan.create(cargo, operation(), registry, boardId,
                releaseBox.getSelection(), offlineBox.getSelection());
    }

    private void updatePlan() {
        if (status == null || status.isDisposed()) return;
        if (running.get()) return;
        runButton.setEnabled(tools != null && tools.readyForCargo()
                && selectedProject() != null);
        try {
            CargoPlan plan = createPlan();
            StringBuilder detail = new StringBuilder();
            detail.append("Manifest: ").append(plan.project().manifest()).append("\n");
            detail.append("Action: ").append(plan.operation()).append("\n");
            detail.append("Target: ").append(plan.rustTarget().isBlank()
                    ? "Host" : plan.rustTarget() + " (candidate only)").append("\n");
            detail.append("Command: ").append(String.join(" ", plan.command())).append("\n\n");
            detail.append(plan.safetyNotice()).append("\n\n");
            if (tools == null || !tools.readyForCargo()) {
                detail.append("Rust/Cargo is not verified. Click Setup Rust & Cargo..., "
                        + "then Verify Tools after installation.\n\n");
            }
            detail.append("Build/check/test logs open in the Rust Cargo Eclipse Console. "
                    + "No firmware is flashed or debugged by these actions.");
            status.setText(detail.toString());
        } catch (Exception error) {
            status.setText(error.getMessage());
        }
    }

    private static MessageConsole console() {
        String name = "Rust Embedded — Cargo";
        var manager = ConsolePlugin.getDefault().getConsoleManager();
        for (IConsole item : manager.getConsoles())
            if (item instanceof MessageConsole c && name.equals(c.getName()))
                return c;
        MessageConsole created = new MessageConsole(name, null);
        manager.addConsoles(new IConsole[] {created});
        return created;
    }

    private void runCargo() {
        if (tools == null || !tools.readyForCargo()) {
            MessageDialog.openInformation(getSite().getShell(), "Rust toolchain required",
                    "Rustc and Cargo are not verified. Open Setup Rust & Cargo... "
                    + "to install the host essentials, then Verify Tools.");
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        try {
            CargoPlan plan = createPlan();
            if (!MessageDialog.openConfirm(getSite().getShell(), "Approve Cargo operation",
                    plan.safetyNotice() + "\n\nExact command:\n"
                    + String.join(" ", plan.command()) + "\n\nRun Cargo now?")) {
                running.set(false);
                return;
            }
            MessageConsole output = console();
            output.activate();
            ConsolePlugin.getDefault().getConsoleManager().showConsoleView(output);
            MessageConsoleStream stream = output.newMessageStream();
            runButton.setEnabled(false);
            cancelButton.setEnabled(true);
            status.setText("Running " + plan.operation() + " for " + plan.project().manifest()
                    + "\nSee Rust Embedded — Cargo in the Eclipse Console view.");
            Job job = new Job("Rust Cargo " + plan.operation()) {
                @Override protected IStatus run(IProgressMonitor monitor) {
                    try {
                        stream.println("\n===== Rust Cargo " + plan.operation() + " =====");
                        stream.println("Manifest: " + plan.project().manifest());
                        stream.println("Target: " + (plan.rustTarget().isBlank()
                                ? "host" : plan.rustTarget() + " (candidate only)"));
                        var result = new CargoExecutor(new CargoExecutor.LocalRunner())
                                .run(plan, true, monitor::isCanceled, line -> {
                                    try {
                                        stream.write(line.getBytes(StandardCharsets.UTF_8));
                                    } catch (IOException error) {
                                        // Do not conceal log transport failures.
                                        throw new java.io.UncheckedIOException(error);
                                    }
                                });
                        String conclusion = "Cargo exited " + result.exitCode()
                                + " after " + result.elapsedMillis() + " ms"
                                + (result.truncated() ? " (log truncated)" : "");
                        stream.println(conclusion);
                        if (plan.operation() != CargoPlan.Operation.METADATA
                                && plan.operation() != CargoPlan.Operation.CLEAN) {
                            var diagnostics = CargoDiagnostics.parse(plan.project(), result.log());
                            int count = CargoProblems.publish(plan.project(), diagnostics);
                            stream.println("Eclipse Problems: " + count
                                    + " project-local rustc diagnostic markers ("
                                    + "unrecognised diagnostic formats remain in the Console).");
                        }
                        onUi(() -> status.setText(conclusion
                                + "\nLog is in Rust Embedded — Cargo Console.\n"
                                + "This is not evidence of flashing/debugging."));
                        return result.succeeded() ? Status.OK_STATUS
                                : new Status(IStatus.ERROR, Activator.ID, conclusion);
                    } catch (java.io.InterruptedIOException cancelled) {
                        return Status.CANCEL_STATUS;
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return Status.CANCEL_STATUS;
                    } catch (Exception error) {
                        try { stream.println("Cargo failed: " + error.getMessage()); }
                        catch (Exception ignored) { }
                        onUi(() -> status.setText("Cargo failed: " + error.getMessage()
                                + "\nCheck Rust installation, selected project and Console."));
                        return new Status(IStatus.ERROR, Activator.ID,
                                "Cargo command failed", error);
                    } finally {
                        try { stream.close(); }
                        catch (IOException ignored) { }
                        running.set(false);
                        onUi(() -> {
                            runButton.setEnabled(true);
                            cancelButton.setEnabled(false);
                            currentJob = null;
                        });
                    }
                }
            };
            currentJob = job;
            job.setUser(true);
            job.schedule();
        } catch (Exception error) {
            running.set(false);
            MessageDialog.openError(getSite().getShell(), "Rust Cargo",
                    "Could not prepare Cargo command: " + error.getMessage());
        }
    }

    /** Nonblocking, bounded host-tool version checks. */
    private void verifyToolchain() {
        if (!toolScan.compareAndSet(false, true)) return;
        if (toolStatus == null || toolStatus.isDisposed()) return;
        toolStatus.setText("Rust toolchain: verifying installed host tools...");
        if (verifyButton != null) verifyButton.setEnabled(false);
        var job = new Job("Cargo Workbench host-tool verification") {
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    var result = HostToolchain.inspect(monitor::isCanceled);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    tools = result;
                    onUi(() -> {
                        toolStatus.setText(result.readyForCargo()
                                ? "Rust compiler and Cargo are ready. "
                                  + "Choose a Cargo.toml project below."
                                : "Rust/Cargo missing or unusable. "
                                  + "Click Setup Rust & Cargo... to install essentials.");
                        verifyButton.setEnabled(true);
                        toolStatus.getParent().layout(true, true);
                        updatePlan();
                    });
                    return Status.OK_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    onUi(() -> {
                        toolStatus.setText("Rust tool check failed: " + error.getMessage());
                        verifyButton.setEnabled(true);
                    });
                    return new Status(IStatus.WARNING, Activator.ID,
                            "Cannot verify Rust toolchain", error);
                } finally {
                    toolScan.set(false);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private void onUi(Runnable update) {
        if (!PlatformUI.isWorkbenchRunning()) return;
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (status != null && !status.isDisposed()) update.run();
        });
    }

    @Override public void setFocus() {
        if (projectBox != null && !projectBox.isDisposed()) projectBox.setFocus();
    }

    @Override public void dispose() {
        if (currentJob != null) currentJob.cancel();
        super.dispose();
    }
}
