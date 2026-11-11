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
import io.github.niwantha33.rew.core.ToolDetector;
import io.github.niwantha33.rew.setup.SetupCatalog;
import io.github.niwantha33.rew.setup.SetupExecutor;

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
    private final AtomicBoolean running = new AtomicBoolean();

    @Override public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(2, false));
        Label notice = new Label(parent, SWT.WRAP);
        notice.setText("SETUP & DRIVERS — Choose a target, review each operation, then approve an install. "
                + "USB drivers are vendor-managed and never changed automatically.");
        notice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        new Label(parent, SWT.NONE).setText("Target board:");
        boardChoice = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        boardChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        Table table = viewer.getTable();
        table.setLinesVisible(true);
        table.setHeaderVisible(true);
        GridData layout = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        layout.heightHint = 220;
        table.setLayoutData(layout);
        col("Setup component", 240, s -> s.label());
        col("Category", 95, s -> s.kind().toString());
        col("Action", 110, s -> s.mode() == SetupCatalog.Mode.MANUAL
                ? "Vendor / OS" : "Approve install");
        col("Observation", 190, this::status);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        details = new Text(parent, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData d = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
        d.heightHint = 142;
        details.setLayoutData(d);

        Composite controls = new Composite(parent, SWT.NONE);
        controls.setLayout(new GridLayout(3, false));
        controls.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        Button refresh = new Button(controls, SWT.PUSH);
        refresh.setText("Refresh Setup Status");
        refresh.addListener(SWT.Selection, event -> refresh());
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
            for (Board board : boards)
                boardChoice.add(board.vendor() + " — " + board.board() + " (" + board.mcu() + ")");
            if (!boards.isEmpty()) {
                boardChoice.select(0);
                refresh();
            }
        } catch (IOException | IllegalArgumentException error) {
            details.setText("Bundled board catalog is unavailable: " + error.getMessage());
            Activator.getDefault().getLog().log(
                    new Status(IStatus.ERROR, Activator.ID, "Setup catalog invalid", error));
        }
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
        return index >= 0 && index < boards.size() ? boards.get(index) : null;
    }

    private SetupCatalog.Step selected() {
        if (viewer == null) return null;
        IStructuredSelection selection = viewer.getStructuredSelection();
        return selection.getFirstElement() instanceof SetupCatalog.Step step ? step : null;
    }

    private String status(SetupCatalog.Step step) {
        if (step.kind() == SetupCatalog.Kind.DRIVER)
            return "Driver status unknown — inspect device";
        if (step.executableCheck() == null || step.executableCheck().isBlank())
            return "Not verified; review required";
        return ToolDetector.isOnCurrentPath(step.executableCheck())
                ? "On PATH (version unverified)" : "Not found on PATH";
    }

    private void refresh() {
        Board selectedBoard = board();
        if (selectedBoard == null) return;
        steps = SetupCatalog.forBoard(selectedBoard, SetupExecutor.isWindows());
        viewer.setInput(steps);
        viewer.refresh();
        install.setEnabled(false);
        official.setEnabled(false);
        details.setText("Select a setup component. Tool presence checks examine PATH only; "
                + "they do NOT verify tool version, connected hardware, a working USB driver "
                + "or a successful Rust build. Driver setup must be completed with the vendor.");
    }

    private void describeSelected() {
        SetupCatalog.Step step = selected();
        official.setEnabled(step != null);
        install.setEnabled(step != null && step.mode() == SetupCatalog.Mode.MANAGED
                && !running.get());
        if (step == null) return;
        details.setText(step.label() + "\n\n" + step.explanation()
                + "\n\nPlanned action: " + step.displayCommand()
                + "\nStatus: " + status(step)
                + "\nOfficial source: " + step.documentation()
                + "\n\nThe workbench never modifies Windows USB drivers or MCU firmware automatically.");
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
        if (board == null || step == null || step.mode() != SetupCatalog.Mode.MANAGED
                || !running.compareAndSet(false, true)) return;
        String message = "Target: " + board.board()
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
                    var result = new SetupExecutor(new SetupExecutor.LocalProcess())
                            .install(registry, board.id(), step.id(), true, monitor::isCanceled);
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
        install.setEnabled(step != null && step.mode() == SetupCatalog.Mode.MANAGED
                && !running.get());
    }

    @Override public void setFocus() {
        if (boardChoice != null && !boardChoice.isDisposed()) boardChoice.setFocus();
    }
}
