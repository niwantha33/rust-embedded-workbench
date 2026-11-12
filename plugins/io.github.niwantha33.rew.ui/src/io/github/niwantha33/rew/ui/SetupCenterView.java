// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;
import io.github.niwantha33.rew.setup.SetupReadiness;
import io.github.niwantha33.rew.setup.SetupCatalog;
import io.github.niwantha33.rew.setup.SetupExecutor;
import io.github.niwantha33.rew.setup.HostToolchain;
import io.github.niwantha33.rew.setup.ProbeScanner;

/**
 * Installation centre: explicit per-step approval, fixed commands, and
 * vendor-controlled driver guidance. No automatic driver/firmware changes.
 */
public final class SetupCenterView extends ViewPart {
    public static final String ID = "io.github.niwantha33.rew.ui.setupCenter";
    private BoardRegistry registry;
    private List<Board> boards = List.of();
    private List<SetupCatalog.Step> steps = List.of();
    private Combo boardChoice;
    private TableViewer viewer;
    private Text details;
    private Button install;
    private Button official;
    private Button essentials;
    private Button cancelSetup;
    private Label toolStatus;
    private Button advancedToggle;
    private Composite advancedPanel;
    private GridData advancedLayout;
    private Composite root;
    private Text boardGuidance;
    private Button vendorDriverGuide;
    private Button scanUsbButton;
    private boolean manuallyChangedAdvanced;
    private Job setupJob;
    private volatile HostToolchain.Report report;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    @Override public void createPartControl(Composite parent) {
        root = parent;
        parent.setLayout(new GridLayout(2, false));

        Label notice = new Label(parent, SWT.WRAP);
        notice.setText("RUST & CARGO SETUP — This screen checks your PC. "
                + "A development board and its USB debugger are separate, optional steps.");
        notice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        toolStatus = new Label(parent, SWT.WRAP);
        toolStatus.setText("Checking Rust and Cargo...");
        toolStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Composite primary = new Composite(parent, SWT.NONE);
        primary.setLayout(new GridLayout(3, false));
        primary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        Button continueButton = new Button(primary, SWT.PUSH);
        continueButton.setText("Continue — Start Here...");
        continueButton.setToolTipText("Open the guided Rust lessons and first PC project.");
        continueButton.addListener(SWT.Selection, event -> {
            try {
                getSite().getPage().showView(StartHereView.ID);
            } catch (org.eclipse.ui.PartInitException error) {
                MessageDialog.openError(getSite().getShell(), "Rust first steps",
                        "Could not open Start Here: " + error.getMessage());
            }
        });
        Button verify = new Button(primary, SWT.PUSH);
        verify.setText("Verify Rust Tools");
        verify.addListener(SWT.Selection, event -> verifyTools());
        advancedToggle = new Button(primary, SWT.PUSH);
        advancedToggle.setText("Show Advanced Setup Details...");
        advancedToggle.addListener(SWT.Selection, event -> {
            manuallyChangedAdvanced = true;
            setAdvancedVisible(advancedLayout.exclude);
        });

        Label boardTitle = new Label(parent, SWT.WRAP);
        boardTitle.setText("OPTIONAL — BOARD SETUP");
        boardTitle.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        new Label(parent, SWT.NONE).setText("Your development board:");
        boardChoice = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        boardChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        boardGuidance = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData boardData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        boardData.heightHint = 110;
        boardGuidance.setLayoutData(boardData);
        Composite driverButtons = new Composite(parent, SWT.NONE);
        driverButtons.setLayout(new GridLayout(2, false));
        driverButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        vendorDriverGuide = new Button(driverButtons, SWT.PUSH);
        vendorDriverGuide.setText("Open Board Driver Guide...");
        vendorDriverGuide.setEnabled(false);
        vendorDriverGuide.addListener(SWT.Selection, event -> openBoardDriverGuide());
        scanUsbButton = new Button(driverButtons, SWT.PUSH);
        scanUsbButton.setText("Scan Connected USB (read only)");
        scanUsbButton.setEnabled(false);
        scanUsbButton.addListener(SWT.Selection, event -> scanConnectedUsb());

        advancedPanel = new Composite(parent, SWT.NONE);
        advancedPanel.setLayout(new GridLayout(1, false));
        advancedLayout = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        advancedPanel.setLayoutData(advancedLayout);

        Composite firstRun = new Composite(advancedPanel, SWT.NONE);
        firstRun.setLayout(new GridLayout(3, false));
        firstRun.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        essentials = new Button(firstRun, SWT.PUSH);
        essentials.setText("Install Rust Essentials...");
        essentials.setEnabled(false);
        essentials.addListener(SWT.Selection, event -> installEssentials());
        cancelSetup = new Button(firstRun, SWT.PUSH);
        cancelSetup.setText("Cancel Setup");
        cancelSetup.setEnabled(false);
        cancelSetup.addListener(SWT.Selection, event -> {
            if (setupJob != null) setupJob.cancel();
        });
        Button officialRust = new Button(firstRun, SWT.PUSH);
        officialRust.setText("Open Official Rust Installer...");
        officialRust.addListener(SWT.Selection, event -> {
            String url = "https://rust-lang.org/tools/install/";
            if (!Program.launch(url))
                MessageDialog.openInformation(getSite().getShell(), "Official Rust installer",
                        "Open this HTTPS URL in your browser:\n" + url);
        });

        viewer = new TableViewer(advancedPanel, SWT.BORDER | SWT.FULL_SELECTION
                | SWT.SINGLE | SWT.V_SCROLL);
        Table table = viewer.getTable();
        table.setLinesVisible(true);
        table.setHeaderVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 190;
        table.setLayoutData(tableData);
        col("Component", 255, SetupReadiness::displayName);
        col("Category", 95, step -> step.kind().toString());
        col("Next action", 150, step -> SetupReadiness.evaluate(step, report).nextAction());
        col("Verified observation", 270, this::status);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        details = new Text(advancedPanel, SWT.MULTI | SWT.WRAP
                | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData detailData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailData.heightHint = 115;
        details.setLayoutData(detailData);
        Composite controls = new Composite(advancedPanel, SWT.NONE);
        controls.setLayout(new GridLayout(3, false));
        controls.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button refresh = new Button(controls, SWT.PUSH);
        refresh.setText("Recheck Installed Tools");
        refresh.addListener(SWT.Selection, event -> verifyTools());
        install = new Button(controls, SWT.PUSH);
        install.setText("Install Selected...");
        install.setEnabled(false);
        install.addListener(SWT.Selection, event -> install());
        official = new Button(controls, SWT.PUSH);
        official.setText("Official Instructions");
        official.setEnabled(false);
        official.addListener(SWT.Selection, event -> openInstructions());

        viewer.addSelectionChangedListener(event -> describeSelected());
        boardChoice.addListener(SWT.Selection, event -> refresh());

        try {
            registry = readCatalog();
            boards = registry.all();
            boardChoice.add("Choose a board (optional — start on this PC)");
            for (Board board : boards)
                boardChoice.add(board.vendor() + " — " + board.board()
                        + " (" + board.mcu() + ")");
            boardChoice.select(0);
            refresh();
        } catch (IOException | IllegalArgumentException error) {
            details.setText("Bundled board catalog is unavailable: " + error.getMessage());
            boardGuidance.setText("Board information is unavailable. "
                    + "You can still learn Rust and use Cargo on this PC.");
            Activator.getDefault().getLog().log(
                    new Status(IStatus.ERROR, Activator.ID, "Setup catalog invalid", error));
        }
        // The empty checks view should not consume the screen before verification.
        setAdvancedVisible(false);
        verifyTools();
    }

    private void setAdvancedVisible(boolean visible) {
        if (advancedPanel == null || advancedPanel.isDisposed()) return;
        advancedLayout.exclude = !visible;
        advancedPanel.setVisible(visible);
        advancedToggle.setText(visible
                ? "Hide Advanced Setup Details" : "Show Advanced Setup Details...");
        root.layout(true, true);
    }

    private SetupCatalog.Step driverStep() {
        return steps.stream()
                .filter(step -> step.kind() == SetupCatalog.Kind.DRIVER)
                .findFirst().orElse(null);
    }

    private void updateBoardGuide() {
        Board board = board();
        boolean selected = board != null;
        vendorDriverGuide.setEnabled(selected && driverStep() != null);
        scanUsbButton.setEnabled(selected && SetupExecutor.isWindows());
        if (!selected) {
            boardGuidance.setText("No hardware selected. Rust and Cargo are enough to start "
                    + "learning on this PC. Choose an actual board only when you need "
                    + "its target, hardware libraries or debugging setup.");
            return;
        }
        SetupCatalog.Step driver = driverStep();
        String description = driver == null ? "No vendor driver instructions available."
                : SetupReadiness.displayName(driver);
        boardGuidance.setText("Board: " + board.board() + " (documentation candidate only).\n"
                + "1. Connect the real board with a data-capable USB cable.\n"
                + "2. Use Scan Connected USB, then inspect the actual interface "
                + "and its driver in Windows Device Manager.\n"
                + "3. Review " + description + " with Open Board Driver Guide. "
                + "Install or change a driver only if the exact interface needs it.\n"
                + "4. Validate the probe with a separate real-board test before any "
                + "build/flash/debug claim. No driver or firmware is changed here.");
    }

    private void openBoardDriverGuide() {
        SetupCatalog.Step driver = driverStep();
        if (driver == null) return;
        boolean opened = Program.launch(driver.documentation().toString());
        if (!opened)
            MessageDialog.openInformation(getSite().getShell(), "Board driver guidance",
                    "Open this HTTPS vendor documentation:\n" + driver.documentation());
    }

    /** Read-only host scan; always off SWT UI thread with bounded version probes. */
    private void verifyTools() {
        if (!scanning.compareAndSet(false, true)) return;
        if (toolStatus != null && !toolStatus.isDisposed())
            toolStatus.setText("Rust host: checking installed tools and versions...");
        Job probe = new Job("Verify installed Rust host tools") {
            @Override protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                try {
                    HostToolchain.Report latest = HostToolchain.inspect(monitor::isCanceled);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    report = latest;
                    onUi(() -> {
                        if (toolStatus.isDisposed()) return;
                        boolean managedAvailable = !HostToolchain.essentials(
                                latest, SetupExecutor.isWindows()).isEmpty();
                        toolStatus.setText(latest.readyForCargo()
                                ? "Setup complete for this PC — Rust and Cargo verified. "
                                    + "Choose Continue — Start Here to create your first project."
                                : managedAvailable
                                    ? "Rust/Cargo needs attention. Review Install Rust Essentials..."
                                    : "Rust/Cargo needs attention. Open Official Rust Installer..., "
                                        + "then recheck.");
                        essentials.setEnabled(!running.get() && managedAvailable);
                        if (!manuallyChangedAdvanced)
                            setAdvancedVisible(!latest.readyForCargo());
                        viewer.refresh();
                        describeButtons();
                        if (selected() == null) {
                            details.setText(latest.readyForCargo()
                                    ? "Your Rust compiler and Cargo are ready. No reinstall is needed. "
                                      + "Items below are checked individually. A board target or driver "
                                      + "is separate from host Rust readiness.\n\n"
                                      + "Select a component to see its evidence and next step. "
                                      + "Start Here offers beginner lessons."
                                    : "Rust host setup is incomplete. Verify Rust Tools, "
                                      + "then review only items marked missing. "
                                      + "Unverified items are not proven absent.");
                        } else {
                            describeSelected();
                        }
                        root.layout(true, true);
                    });
                    return Status.OK_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    onUi(() -> toolStatus.setText("Rust tool verification unavailable: "
                            + error.getMessage()));
                    return new Status(IStatus.WARNING, Activator.ID,
                            "Rust tool verification failed", error);
                } finally {
                    scanning.set(false);
                }
            }
        };
        probe.setUser(true);
        probe.schedule();
    }

    /** One explicit approval for the displayed fixed command sequence. */
    private void installEssentials() {
        HostToolchain.Report latest = report;
        if (latest == null || running.get()) {
            MessageDialog.openInformation(getSite().getShell(), "Rust Essentials",
                    "Run Verify Rust Tools first.");
            return;
        }
        List<String> ids = HostToolchain.essentials(latest, SetupExecutor.isWindows());
        if (ids.isEmpty()) {
            MessageDialog.openInformation(getSite().getShell(), "Rust Essentials",
                    "No managed installation is available or required.\n"
                    + "If Rustup is absent and WinGet is unavailable, click "
                    + "Open Official Rust Installer... and follow the official instructions.");
            return;
        }
        List<SetupCatalog.Step> catalog = SetupCatalog.hostEssentials(SetupExecutor.isWindows());
        StringBuilder summary = new StringBuilder(
                "Only the following fixed, official toolchain/package-manager operations "
                + "will run, in this exact order:\n\n");
        for (String id : ids) {
            var step = SetupCatalog.require(catalog, id);
            summary.append("• ").append(step.label())
                    .append("\n   ").append(step.displayCommand()).append("\n");
        }
        if (ids.contains("install-rustup")) {
            summary.append("\nIMPORTANT: the shown WinGet command explicitly ACCEPTS "
                    + "the WinGet source and Rustup package agreements for you. "
                    + "Review the Rust licence and package terms at "
                    + "https://rust-lang.org/tools/install/ before agreeing. "
                    + "Cancel if you do not accept those terms.\n");
        }
        summary.append("\nThese commands download and run third-party software with your "
                + "Windows/Linux user permissions, and may update your stable Rust compiler. "
                + "They will not install USB drivers, elevate privileges, flash a chip or "
                + "change Eclipse/Zephyr projects. Check package publisher/licences and "
                + "approve Windows prompts. Native Windows builds may also need Visual "
                + "Studio C++ Build Tools.\n\nProceed?");
        if (!MessageDialog.openConfirm(getSite().getShell(),
                "Approve Rust essentials installation", summary.toString()))
            return;
        if (!running.compareAndSet(false, true)) return;
        essentials.setEnabled(false);
        install.setEnabled(false);
        cancelSetup.setEnabled(true);
        Job task = new Job("Install Rust host essentials") {
            @Override protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                var output = new StringBuilder("Rust essentials: approved package-manager steps\n\n");
                SetupExecutor installer = new SetupExecutor(new SetupExecutor.LocalProcess());
                try {
                    monitor.beginTask("Install selected Rust host components", ids.size());
                    for (String id : ids) {
                        if (monitor.isCanceled())
                            throw new java.io.InterruptedIOException("Setup cancelled by user");
                        SetupCatalog.Step step = SetupCatalog.require(catalog, id);
                        monitor.subTask(step.label());
                        appendSetupLog(output, "\n> " + step.displayCommand() + "\n");
                        var result = installer.installHost(id, true, monitor::isCanceled);
                        appendSetupLog(output, result.output() + "\nExit status: "
                                + result.exitCode() + "\n");
                        monitor.worked(1);
                        if (!result.succeeded()) {
                            appendSetupLog(output, "\nStopped on failed step. No later "
                                    + "installation commands were run.\n");
                            return new Status(IStatus.ERROR, Activator.ID,
                                    "Rust host setup stopped at " + step.label()
                                    + " (exit " + result.exitCode() + ")");
                        }
                        if ("install-rustup".equals(id)
                                && HostToolchain.current("rustup") == null) {
                            appendSetupLog(output, "\nRustup installer exited successfully, "
                                    + "but the new rustup.exe was not found in ~/.cargo/bin "
                                    + "or PATH. Restart Eclipse, then Verify Rust Tools "
                                    + "and continue the remaining steps.\n");
                            return new Status(IStatus.WARNING, Activator.ID,
                                    "Restart needed before additional Rust setup");
                        }
                    }
                    appendSetupLog(output, "\nCommands finished. Recheck compiler "
                            + "and component versions; GUI tooling is a separate milestone.\n");
                    return Status.OK_STATUS;
                } catch (java.io.InterruptedIOException error) {
                    appendSetupLog(output, "\nSetup cancelled. Already installed "
                            + "tools remain installed; no rollback is claimed.\n");
                    return Status.CANCEL_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    appendSetupLog(output, "\nSetup failed: " + error.getMessage());
                    return new Status(IStatus.ERROR, Activator.ID,
                            "Rust setup failure", error);
                } finally {
                    running.set(false);
                    onUi(() -> {
                        cancelSetup.setEnabled(false);
                        setupJob = null;
                        describeButtons();
                        verifyTools();
                    });
                }
            }

            private void appendSetupLog(StringBuilder output, String text) {
                output.append(text);
                if (output.length() > 36_000) output.delete(0, output.length() - 36_000);
                String snapshot = output.toString();
                onUi(() -> details.setText(snapshot));
            }
        };
        setupJob = task;
        task.setUser(true);
        task.schedule();
    }

    private void col(String label, int width,
            java.util.function.Function<SetupCatalog.Step, String> format) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setWidth(width);
        column.getColumn().setText(label);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return format.apply((SetupCatalog.Step) element);
            }
        });
    }

    private static BoardRegistry readCatalog() throws IOException {
        try (InputStream stream = Board.class.getResourceAsStream("/catalog/boards.tsv")) {
            if (stream == null) throw new IOException("Missing catalog/boards.tsv");
            Path temporary = Files.createTempFile("rew-setup-catalog-", ".tsv");
            try {
                Files.copy(stream, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return BoardRegistry.load(temporary);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private Board board() {
        int index = boardChoice.getSelectionIndex();
        return index > 0 && index <= boards.size() ? boards.get(index - 1) : null;
    }

    private SetupCatalog.Step selected() {
        if (viewer == null) return null;
        IStructuredSelection selection = viewer.getStructuredSelection();
        return selection.getFirstElement() instanceof SetupCatalog.Step step ? step : null;
    }

    private String status(SetupCatalog.Step step) {
        return SetupReadiness.evaluate(step, report).observation();
    }

    private void refresh() {
        Board selectedBoard = board();
        steps = selectedBoard == null
                ? SetupCatalog.hostEssentials(SetupExecutor.isWindows())
                : SetupCatalog.forBoard(selectedBoard, SetupExecutor.isWindows());
        viewer.setInput(steps);
        viewer.refresh();
        updateBoardGuide();
        install.setEnabled(false);
        official.setEnabled(false);
        details.setText(selectedBoard == null
                ? "No board selected. Check host Rust tools below, or open Start Here "
                    + "to create your first PC project. Select a board to see its targets and drivers."
                : "Board selected: " + selectedBoard.board()
                    + " (documentation candidate, not verified hardware). "
                    + "Choose a row to see what has actually been checked.");
    }

    private void describeSelected() {
        SetupCatalog.Step step = selected();
        official.setEnabled(step != null);
        describeButtons();
        if (step == null) return;
        SetupReadiness.Result readiness = SetupReadiness.evaluate(step, report);
        details.setText(step.label() + "\n\n" + step.explanation()
                + "\n\nObserved: " + readiness.observation()
                + "\nNext: " + readiness.nextAction()
                + "\nPlanned command (only after approval): " + step.displayCommand()
                + "\nOfficial source: " + step.documentation()
                + "\n\nInstalled host tools do not prove a connected probe or MCU build, flash or debug.");
    }

    /** Read-only Windows PnP discovery; never install, disable or rebind a driver. */
    private void scanConnectedUsb() {
        if (!SetupExecutor.isWindows()) return;
        var job = new Job("Scan connected USB vendor identifiers") {
            @Override protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                try {
                    List<ProbeScanner.Observation> observations =
                            ProbeScanner.scanWindows(monitor::isCanceled);
                    StringBuilder text = new StringBuilder(
                            "Present Windows USB vendors (NOT driver verification):\n\n");
                    if (observations.isEmpty()) {
                        text.append("No matching USB vendor IDs detected. Check the connection, "
                                + "board port and Windows Device Manager.");
                    } else {
                        for (var observation : observations) {
                            text.append(observation.vendorHint()).append(" — VID_")
                                    .append(observation.vendorId()).append(" PID_")
                                    .append(observation.productId()).append("\n");
                        }
                    }
                    onUi(() -> boardGuidance.setText(text.toString()
                            + "\nThese are USB vendor hints, not verified board/driver readiness. "
                            + "Inspect the exact device in Windows Device Manager."));
                    return Status.OK_STATUS;
                } catch (java.io.InterruptedIOException error) {
                    return Status.CANCEL_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    onUi(() -> boardGuidance.setText("USB scan unavailable: " + error.getMessage()
                            + "\nNo driver was installed or changed."));
                    return new Status(IStatus.WARNING, Activator.ID,
                            "USB scan unavailable", error);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private void onUi(Runnable action) {
        if (!PlatformUI.isWorkbenchRunning()) return;
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (details != null && !details.isDisposed()) action.run();
        });
    }

    private void openInstructions() {
        SetupCatalog.Step step = selected();
        if (step == null) return;
        boolean opened = Program.launch(step.documentation().toString());
        if (!opened)
            MessageDialog.openInformation(getSite().getShell(), "Official documentation",
                    "Open this trusted HTTPS address in your browser:\n" + step.documentation());
    }

    private void install() {
        Board board = board();
        SetupCatalog.Step step = selected();
        if (step == null || !SetupReadiness.evaluate(step, report).canInstall()
                || !running.compareAndSet(false, true)) return;
        String message = "Target: " + (board == null ? "Host PC" : board.board())
                + "\n\nOperation: " + step.label()
                + "\nExact command: " + step.displayCommand()
                + "\n\n" + step.explanation()
                + "\n\nThis may download and execute software from third-party package sources "
                + "with your current user permissions. Do you approve?";
        if (!MessageDialog.openConfirm(getSite().getShell(), "Approve setup installation", message)) {
            running.set(false);
            return;
        }
        install.setEnabled(false);
        Job job = new Job("Rust setup: " + step.label()) {
            @Override protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                try {
                    SetupExecutor runner = new SetupExecutor(new SetupExecutor.LocalProcess());
                    var result = board == null
                            ? runner.installHost(step.id(), true, monitor::isCanceled)
                            : runner.install(registry, board.id(), step.id(), true, monitor::isCanceled);
                    var status = result.succeeded() ? Status.OK_STATUS
                            : new Status(IStatus.ERROR, Activator.ID,
                                    "Installer returned exit code " + result.exitCode());
                    showResult(step, result, status);
                    return status;
                } catch (java.io.InterruptedIOException error) {
                    return Status.CANCEL_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    showResult(step, null,
                            new Status(IStatus.ERROR, Activator.ID,
                                    "Setup could not complete: " + error.getMessage(), error));
                    return new Status(IStatus.ERROR, Activator.ID,
                            "Setup failed; no success claimed", error);
                } finally {
                    running.set(false);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private void showResult(SetupCatalog.Step step, SetupExecutor.Result result, IStatus status) {
        if (!PlatformUI.isWorkbenchRunning()) return;
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (details == null || details.isDisposed()) return;
            String output = result == null ? "" : result.output();
            details.setText(step.label() + "\n\n" + (status.isOK()
                    ? "Command exited successfully. Recheck versions, IDE PATH and actual hardware."
                    : "Setup did not complete: " + status.getMessage())
                    + "\n\n" + output);
            viewer.refresh();
            describeButtons();
        });
    }
    private void describeButtons() {
        SetupCatalog.Step step = selected();
        install.setEnabled(step != null && !running.get()
                && SetupReadiness.evaluate(step, report).canInstall());
    }

    /** Called by Board Explorer so setup opens with the exact chosen target. */
    public void selectBoard(String boardId) {
        if (boardChoice == null || boardChoice.isDisposed()) return;
        for (int i = 0; i < boards.size(); i++) {
            if (boards.get(i).id().equals(boardId)) {
                boardChoice.select(i + 1);
                refresh();
                return;
            }
        }
    }

    @Override public void dispose() {
        if (setupJob != null) setupJob.cancel();
        super.dispose();
    }

    @Override public void setFocus() {
        if (boardChoice != null && !boardChoice.isDisposed()) boardChoice.setFocus();
    }
}
