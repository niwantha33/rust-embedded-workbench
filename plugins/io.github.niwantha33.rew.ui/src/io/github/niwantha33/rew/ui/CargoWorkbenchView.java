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
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FillLayout;
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
import io.github.niwantha33.rew.core.StarterProject;
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
    private Text planDetails;
    private Label guideText;
    private Composite guidePanel;
    private ScrolledComposite guideScroll;
    private GridData planLayout;
    private Job currentJob;
    private final AtomicBoolean running = new AtomicBoolean();

    @Override public void createPartControl(Composite parent) {
        // Adjustable left/right columns keep the beginner workflow separate from results.
        parent.setLayout(new FillLayout());
        SashForm columns = new SashForm(parent, SWT.HORIZONTAL | SWT.SMOOTH);

        ScrolledComposite leftScroll = new ScrolledComposite(columns, SWT.V_SCROLL | SWT.H_SCROLL);
        leftScroll.setExpandHorizontal(true);
        leftScroll.setExpandVertical(true);
        Composite controls = new Composite(leftScroll, SWT.NONE);
        controls.setLayout(new GridLayout(2, false));

        Label leftHeading = new Label(controls, SWT.WRAP);
        leftHeading.setText("1. YOUR PROJECT AND ACTION");
        leftHeading.setLayoutData(spanTwo());
        Label leftHelp = new Label(controls, SWT.WRAP);
        leftHelp.setText("Choose your Rust project, choose what to do, then press Run. "
                + "Start with Check on this PC. No microcontroller is needed.");
        leftHelp.setLayoutData(spanTwo());

        Label projectLabel = new Label(controls, SWT.NONE);
        projectLabel.setText("Rust project:");
        projectLabel.setLayoutData(spanTwo());
        projectBox = new Combo(controls, SWT.DROP_DOWN | SWT.READ_ONLY);
        projectBox.setLayoutData(spanTwo());

        Button firstProject = new Button(controls, SWT.PUSH);
        firstProject.setText("Create My First Rust Project...");
        firstProject.setLayoutData(spanTwo());
        firstProject.setToolTipText("Creates a new PC Hello World project only in a new folder. "
                + "No development board is needed.");
        firstProject.addListener(SWT.Selection, event -> createFirstProject());

        Button importButton = new Button(controls, SWT.PUSH);
        importButton.setText("Import Existing...");
        importButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        importButton.addListener(SWT.Selection, event -> importExisting());
        Button refreshButton = new Button(controls, SWT.PUSH);
        refreshButton.setText("Refresh Projects");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refreshButton.addListener(SWT.Selection, event -> refreshProjects(null));

        Label actionLabel = new Label(controls, SWT.NONE);
        actionLabel.setText("What do you want to do?");
        actionLabel.setLayoutData(spanTwo());
        operationBox = new Combo(controls, SWT.DROP_DOWN | SWT.READ_ONLY);
        operationBox.setItems(new String[] {
                "Check", "Build", "Clippy", "Test (HOST ONLY)", "Clean", "Metadata (read only)"
        });
        operationBox.select(0);
        operationBox.setLayoutData(spanTwo());

        Label targetLabel = new Label(controls, SWT.NONE);
        targetLabel.setText("Where should the code be built?");
        targetLabel.setLayoutData(spanTwo());
        targetBox = new Combo(controls, SWT.DROP_DOWN | SWT.READ_ONLY);
        targetBox.setLayoutData(spanTwo());
        targetBox.add("This PC (Host — start here)");
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

        releaseBox = new Button(controls, SWT.CHECK);
        releaseBox.setText("Optimized release build");
        releaseBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        offlineBox = new Button(controls, SWT.CHECK);
        offlineBox.setText("Work offline");
        offlineBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        releaseBox.addListener(SWT.Selection, event -> updatePlan());
        offlineBox.addListener(SWT.Selection, event -> updatePlan());

        runButton = new Button(controls, SWT.PUSH);
        runButton.setText("Run Selected Cargo Action...");
        runButton.setEnabled(false);
        runButton.setLayoutData(spanTwo());
        runButton.addListener(SWT.Selection, event -> runCargo());
        cancelButton = new Button(controls, SWT.PUSH);
        cancelButton.setText("Cancel Running Job");
        cancelButton.setLayoutData(spanTwo());
        cancelButton.setEnabled(false);
        cancelButton.addListener(SWT.Selection, event -> {
            if (currentJob != null) currentJob.cancel();
        });

        Label leftFooter = new Label(controls, SWT.WRAP);
        leftFooter.setText("Need help with Rust installation?");
        leftFooter.setLayoutData(spanTwo());
        Button setupButton = new Button(controls, SWT.PUSH);
        setupButton.setText("Setup Rust & Cargo...");
        setupButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        setupButton.addListener(SWT.Selection, event -> {
            try {
                getSite().getPage().showView(SetupCenterView.ID);
            } catch (org.eclipse.ui.PartInitException error) {
                MessageDialog.openError(getSite().getShell(), "Cargo tools",
                        "Cannot open Setup & Drivers: " + error.getMessage());
            }
        });
        verifyButton = new Button(controls, SWT.PUSH);
        verifyButton.setText("Verify Tools");
        verifyButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        verifyButton.addListener(SWT.Selection, event -> verifyToolchain());

        leftScroll.setContent(controls);
        leftScroll.setMinSize(controls.computeSize(330, SWT.DEFAULT));

        guideScroll = new ScrolledComposite(columns, SWT.V_SCROLL | SWT.H_SCROLL);
        guideScroll.setExpandHorizontal(true);
        guideScroll.setExpandVertical(true);
        guidePanel = new Composite(guideScroll, SWT.NONE);
        guidePanel.setLayout(new GridLayout(1, false));

        Label rightHeading = new Label(guidePanel, SWT.WRAP);
        rightHeading.setText("2. GUIDANCE AND RESULTS");
        rightHeading.setLayoutData(fillRow());

        toolStatus = new Label(guidePanel, SWT.WRAP);
        toolStatus.setText("Checking Rust and Cargo...");
        toolStatus.setLayoutData(fillRow());

        Label guideHeading = new Label(guidePanel, SWT.NONE);
        guideHeading.setText("What will happen?");
        guideHeading.setLayoutData(fillRow());
        guideText = new Label(guidePanel, SWT.WRAP);
        guideText.setLayoutData(fillRow());

        Label resultHeading = new Label(guidePanel, SWT.NONE);
        resultHeading.setText("Last Cargo result");
        resultHeading.setLayoutData(fillRow());
        status = new Text(guidePanel, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI
                | SWT.WRAP | SWT.V_SCROLL);
        GridData resultLayout = new GridData(SWT.FILL, SWT.FILL, true, false);
        resultLayout.heightHint = 135;
        status.setLayoutData(resultLayout);
        status.setText("No action has run yet. Select a project on the left, "
                + "choose Check, and press Run Selected Cargo Action.");

        Label note = new Label(guidePanel, SWT.WRAP);
        note.setText("Cargo may download dependencies or run their build scripts. "
                + "Before each action, you can review and approve the exact command. "
                + "A successful PC check is not proof of flashing or debugging a board.");
        note.setLayoutData(fillRow());

        Button expertToggle = new Button(guidePanel, SWT.CHECK);
        expertToggle.setText("Expert: show exact Cargo command and safety details");
        expertToggle.setLayoutData(fillRow());
        planDetails = new Text(guidePanel, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI
                | SWT.WRAP | SWT.V_SCROLL);
        planLayout = new GridData(SWT.FILL, SWT.FILL, true, false);
        planLayout.heightHint = 175;
        planLayout.exclude = true;
        planDetails.setLayoutData(planLayout);
        planDetails.setVisible(false);
        expertToggle.addListener(SWT.Selection, event -> {
            boolean show = expertToggle.getSelection();
            planLayout.exclude = !show;
            planDetails.setVisible(show);
            guidePanel.layout(true, true);
            guideScroll.setMinSize(guidePanel.computeSize(330, SWT.DEFAULT));
        });
        guideScroll.setContent(guidePanel);
        guideScroll.setMinSize(guidePanel.computeSize(330, SWT.DEFAULT));
        columns.setWeights(new int[] {45, 55});

        refreshProjects(null);
        verifyToolchain();
    }

    private static GridData spanTwo() {
        return new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    }

    private static GridData fillRow() {
        return new GridData(SWT.FILL, SWT.CENTER, true, false);
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
        picker.setMessage("Sources stay in their original location; Eclipse links to them.");
        String selected = picker.open();
        if (selected != null) importFromDirectory(Path.of(selected));
    }

    /** Called by the beginner guide; user-chosen folders are never copied. */
    public void importFromDirectory(Path directory) {
        try {
            CargoProject cargo = CargoProject.open(directory.resolve("Cargo.toml"));
            linkProject(cargo, true);
        } catch (Exception error) {
            MessageDialog.openError(getSite().getShell(), "Rust project import",
                    "Could not link the selected Cargo project:\n" + error.getMessage());
        }
    }

    /** Creates only a new host project, never an MCU project or an existing directory. */
    public void createFirstProject() {
        DirectoryDialog picker = new DirectoryDialog(getSite().getShell());
        picker.setText("Choose a parent folder for a new Rust project");
        picker.setMessage("A new project folder is created inside this location; existing files stay untouched.");
        String parentFolder = picker.open();
        if (parentFolder == null) return;
        InputDialog nameInput = new InputDialog(getSite().getShell(), "Your first Rust project",
                "Project name: lowercase letters, digits, underscores. "
                + "Start with a letter.", "hello_rust",
                value -> StarterProject.validName(value) ? null
                        : "Use 1–48 lowercase letters, digits and underscores; start with a letter.");
        if (nameInput.open() != Window.OK) return;
        String packageName = nameInput.getValue();
        Path destination = Path.of(parentFolder).resolve(packageName);
        if (!MessageDialog.openConfirm(getSite().getShell(), "Create first Rust project",
                "Create a brand-new host Rust project at:\n" + destination
                + "\n\nFiles: Cargo.toml and src/main.rs."
                + "\nThis first lesson runs on your PC, NOT an MCU."
                + "\nNo existing directory will be overwritten. "
                + "The new folder will be linked into the Eclipse workspace."
                + "\n\nContinue?")) return;
        try {
            StarterProject.Created created = StarterProject.create(Path.of(parentFolder), packageName);
            // The preceding confirmation approves both creation and Eclipse linking.
            linkProject(CargoProject.open(created.manifest()), false);
            operationBox.select(0); // Host Check is a safe first learning action.
            targetBox.select(0);
            updatePlan();
            status.setText("First Rust project created: " + destination
                    + "\nNext: select Check and click Run Cargo..."
                    + "\nThis verifies the host project only; it does not build MCU firmware.");
        } catch (Exception error) {
            MessageDialog.openError(getSite().getShell(), "Create Rust project",
                    "Could not complete the first-project setup:\n" + error.getMessage()
                    + "\nIf files were created, they remain in your chosen folder. "
                    + "You can import them with Import Existing...");
        }
    }

    private void linkProject(CargoProject cargo, boolean confirmLink) throws Exception {
        if (confirmLink && !MessageDialog.openConfirm(getSite().getShell(),
                "Link existing Cargo project",
                "Create a new Eclipse project linking to:\n" + cargo.directory()
                + "\n\nThe Rust sources remain in their original directory. "
                + "Editing or deleting source files in Eclipse affects the originals. "
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
        // The Eclipse .project metadata lives in the workspace, not in the Rust source.
        project.create(null);
        project.open(null);
        IFolder source = project.getFolder("source");
        source.createLink(new org.eclipse.core.runtime.Path(
                cargo.directory().toString()), IResource.NONE, null);
        refreshProjects(cargo.manifest());
        status.setText("Linked Cargo project into Eclipse as " + name
                + "/source. Files remain at " + cargo.directory()
                + "\nEditing/deleting linked source files affects the originals.");
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
        if (status == null || status.isDisposed() || guideText == null
                || guideText.isDisposed()) return;
        if (running.get()) return;
        runButton.setEnabled(tools != null && tools.readyForCargo()
                && selectedProject() != null);
        try {
            CargoPlan plan = createPlan();
            boolean host = plan.rustTarget().isBlank();
            String meaning = switch (plan.operation()) {
                case CHECK -> "Check your Rust code for errors without running the program.";
                case BUILD -> "Compile your Rust project. A PC build can create an executable.";
                case CLIPPY -> "Ask Clippy to look for common Rust mistakes and improvements.";
                case TEST_HOST -> "Run Rust tests on your computer, not on a microcontroller.";
                case CLEAN -> "Remove generated build outputs for this Cargo project.";
                case METADATA -> "Read the project's Cargo metadata without building it.";
            };
            guideText.setText(meaning + "\n\nTarget: " + (host
                    ? "Your PC. This is the recommended first step."
                    : "Candidate MCU target " + plan.rustTarget()
                      + ". Hardware support and flashing are NOT verified.")
                    + "\n\nNext: Review the action, then click Run on the left. "
                    + "The workbench asks for approval before starting.");
            StringBuilder detail = new StringBuilder();
            detail.append("Manifest: ").append(plan.project().manifest()).append("\n");
            detail.append("Action: ").append(plan.operation()).append("\n");
            detail.append("Target: ").append(host
                    ? "Host" : plan.rustTarget() + " (candidate only)").append("\n");
            detail.append("Command: ").append(String.join(" ", plan.command())).append("\n\n");
            detail.append(plan.safetyNotice()).append("\n\n");
            if (tools == null || !tools.readyForCargo()) {
                detail.append("Rust/Cargo is not verified. Click Setup Rust & Cargo..., "
                        + "then Verify Tools after installation.\n\n");
            }
            detail.append("Build/check/test logs open in the Rust Cargo Eclipse Console. "
                    + "No firmware is flashed or debugged by these actions.");
            planDetails.setText(detail.toString());
        } catch (Exception error) {
            guideText.setText("First choose or create a Cargo project on the left. "
                    + "Then select Check to verify your Rust code on this PC.");
            planDetails.setText("No runnable Cargo plan: " + error.getMessage());
        }
        guidePanel.layout(true, true);
        guideScroll.setMinSize(guidePanel.computeSize(330, SWT.DEFAULT));
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
                        onUi(() -> status.setText("Cargo action cancelled. "
                                + "Check the Rust Cargo Console for output. "
                                + "Already-written build files may remain."));
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
                            cancelButton.setEnabled(false);
                            updatePlan();
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
