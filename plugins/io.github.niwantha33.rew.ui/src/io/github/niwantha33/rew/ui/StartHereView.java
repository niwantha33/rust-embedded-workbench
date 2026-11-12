// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;
import io.github.niwantha33.rew.setup.HostToolchain;
import io.github.niwantha33.rew.setup.SetupExecutor;

/**
 * Beginner-first start screen. All claims are limited to actually checked host
 * commands or catalog documentation, never inferred MCU capability.
 */
public final class StartHereView extends ViewPart {
    public static final String ID = "io.github.niwantha33.rew.ui.startHere";
    private static final String MODE_PREFERENCE = "onboarding.expertMode";
    private record Lesson(String title, String explanation, String url) {}
    private static final List<Lesson> LESSONS = List.of(
            new Lesson("1. What are Rust and Cargo?",
                    "Rust is a programming language. Cargo manages Rust projects and builds. "
                    + "Begin with ownership, variables, functions and error handling. "
                    + "No development board is needed for these lessons.",
                    "https://doc.rust-lang.org/book/"),
            new Lesson("2. My first PC program",
                    "Create a host Hello World project. The generated files are Cargo.toml "
                    + "(project configuration) and src/main.rs (your code). Run Cargo Check "
                    + "to validate the code. Cargo Build is a separate step and may need "
                    + "Microsoft C++ linker tools on Windows.",
                    "https://doc.rust-lang.org/cargo/"),
            new Lesson("3. From PC to microcontroller",
                    "A microcontroller does not run desktop programs directly. Learn what a "
                    + "target triple, no_std, HAL, linker script and startup runtime mean. "
                    + "Choose your exact board before attempting a firmware build.",
                    "https://docs.rust-embedded.org/book/"),
            new Lesson("4. GPIO and peripherals",
                    "Learn about digital pins, input/output, pull resistors, timers, UART, "
                    + "SPI, I2C and ADC. The pin mapping and HAL APIs depend on the "
                    + "specific chip and firmware framework.",
                    "https://docs.rust-embedded.org/book/"),
            new Lesson("5. Interrupts and concurrency",
                    "Learn interrupt handlers, shared mutable state, critical sections "
                    + "and embedded task models. Concurrency features vary by HAL "
                    + "and runtime; no RTOS is configured by this preview.",
                    "https://docs.rust-embedded.org/book/"),
            new Lesson("6. Debugging and hardware evidence",
                    "Learn probes, SWD/JTAG, flashing, breakpoints, registers, "
                    + "memory and hardware fault diagnosis. This preview can describe "
                    + "candidate debuggers, but has not validated flash/debug on these boards.",
                    "https://probe.rs/docs/getting-started/"),
            new Lesson("7. Advanced and production",
                    "Explore embedded async frameworks, RTOS integration, testing, "
                    + "CI, code-size analysis, secure boot and reproducible releases. "
                    + "These are learning milestones, not implemented IDE functions.",
                    "https://docs.rust-embedded.org/book/"));

    private final AtomicBoolean scanning = new AtomicBoolean();
    private Label readiness;
    private Combo modeBox;
    private Combo boardBox;
    private Combo lessonBox;
    private Text lessonText;
    private Text expertText;
    private GridData expertLayout;
    private ScrolledComposite scroll;
    private Composite content;
    private List<Board> boardList = List.of();
    private HostToolchain.Report latest;

    @Override public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());
        scroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
        scroll.setExpandHorizontal(true);
        scroll.setExpandVertical(true);
        content = new Composite(scroll, SWT.NONE);
        content.setLayout(new GridLayout(2, false));

        Label heading = new Label(content, SWT.WRAP);
        heading.setText("WELCOME TO RUST EMBEDDED WORKBENCH");
        heading.setLayoutData(fullRow());
        Label purpose = new Label(content, SWT.WRAP);
        purpose.setText("No Rust experience? Start with step 1. "
                + "Already an embedded developer? Switch to Expert for technical details. "
                + "Nothing installs, builds or flashes without an explicit action.");
        purpose.setLayoutData(fullRow());

        new Label(content, SWT.NONE).setText("My experience:");
        modeBox = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        modeBox.setItems(new String[] {"Guided — new to Rust", "Expert — show details"});
        modeBox.select(Activator.getDefault().getPreferenceStore()
                .getBoolean(MODE_PREFERENCE) ? 1 : 0);
        modeBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modeBox.addListener(SWT.Selection, event -> changeMode());

        section("STEP 1 — Check this computer");
        readiness = new Label(content, SWT.WRAP);
        readiness.setText("Checking Rust and Cargo safely...");
        readiness.setLayoutData(fullRow());
        Button verify = new Button(content, SWT.PUSH);
        verify.setText("Recheck Rust & Cargo");
        verify.addListener(SWT.Selection, event -> verifyTools());
        Button install = new Button(content, SWT.PUSH);
        install.setText("Setup or repair Rust...");
        install.addListener(SWT.Selection, event -> showView(SetupCenterView.ID));
        expertText = new Text(content, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY
                | SWT.V_SCROLL | SWT.H_SCROLL);
        expertLayout = fullRow();
        expertLayout.heightHint = 150;
        expertText.setLayoutData(expertLayout);

        section("STEP 2 — Your first program");
        Label hostHelp = new Label(content, SWT.WRAP);
        hostHelp.setText("Start on your PC: create a Hello World project in a new folder. "
                + "Then use Cargo Check to check the code. No board or USB driver is required.");
        hostHelp.setLayoutData(fullRow());
        Button first = new Button(content, SWT.PUSH);
        first.setText("Create My First Rust Project...");
        first.addListener(SWT.Selection, event -> {
            try {
                var part = getSite().getPage().showView(CargoWorkbenchView.ID);
                if (part instanceof CargoWorkbenchView cargo) cargo.createFirstProject();
            } catch (org.eclipse.ui.PartInitException error) {
                MessageDialog.openError(getSite().getShell(), "First project",
                        "Could not open Cargo Workbench: " + error.getMessage());
            }
        });
        Button existing = new Button(content, SWT.PUSH);
        existing.setText("Open Cargo Workbench...");
        existing.addListener(SWT.Selection, event -> showView(CargoWorkbenchView.ID));

        section("STEP 3 — Choose hardware (optional until you know Rust)");
        Label boardHelp = new Label(content, SWT.WRAP);
        boardHelp.setText("Select a specific board only when you want embedded firmware. "
                + "Listed boards are documentation candidates, NOT verified builds or debuggers.");
        boardHelp.setLayoutData(fullRow());
        new Label(content, SWT.NONE).setText("My board:");
        boardBox = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        boardBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        boardBox.add("No board yet — learn on this PC");
        boardBox.select(0);
        try {
            boardList = loadBoards();
            for (Board board : boardList)
                boardBox.add(board.vendor() + " — " + board.board());
        } catch (Exception error) {
            boardBox.setEnabled(false);
            readiness.setText("Board catalogue unavailable. Host Rust lessons still work.");
        }
        Button boardSetup = new Button(content, SWT.PUSH);
        boardSetup.setText("Show setup for selected board...");
        boardSetup.addListener(SWT.Selection, event -> openBoardSetup());
        Button browse = new Button(content, SWT.PUSH);
        browse.setText("Explore all boards...");
        browse.addListener(SWT.Selection, event -> showView(BoardExplorerView.ID));

        section("STEP 4 — Learn at your own pace");
        lessonBox = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        lessonBox.setLayoutData(fullRow());
        for (Lesson lesson : LESSONS) lessonBox.add(lesson.title());
        lessonBox.select(0);
        lessonText = new Text(content, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
        GridData lessonLayout = fullRow();
        lessonLayout.heightHint = 100;
        lessonText.setLayoutData(lessonLayout);
        lessonBox.addListener(SWT.Selection, event -> showLesson());
        Button guide = new Button(content, SWT.PUSH);
        guide.setText("Read official lesson...");
        guide.addListener(SWT.Selection, event -> {
            String url = LESSONS.get(lessonBox.getSelectionIndex()).url();
            if (!Program.launch(url))
                MessageDialog.openInformation(getSite().getShell(), "Official documentation",
                        "Open this HTTPS URL in your browser:\n" + url);
        });
        Button advanced = new Button(content, SWT.PUSH);
        advanced.setText("Open expert Cargo controls...");
        advanced.addListener(SWT.Selection, event -> {
            modeBox.select(1);
            changeMode();
            showView(CargoWorkbenchView.ID);
        });
        Label limits = new Label(content, SWT.WRAP);
        limits.setLayoutData(fullRow());
        limits.setText("Scope: These lessons are guidance. Host tools can be checked, "
                + "and a host project can be created. MCU build/flash/debug remains "
                + "unqualified until tested on exact hardware.");

        scroll.setContent(content);
        changeMode();
        showLesson();
        scroll.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        verifyTools();
    }

    private GridData fullRow() {
        return new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    }

    private void section(String title) {
        Label label = new Label(content, SWT.WRAP);
        label.setText(title);
        GridData data = fullRow();
        data.verticalIndent = 14;
        label.setLayoutData(data);
    }

    private void changeMode() {
        boolean expert = modeBox.getSelectionIndex() == 1;
        Activator.getDefault().getPreferenceStore().setValue(MODE_PREFERENCE, expert);
        expertLayout.exclude = !expert;
        expertText.setVisible(expert);
        if (latest != null) expertText.setText(latest.summary());
        content.layout(true, true);
        scroll.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    private void showLesson() {
        int i = lessonBox.getSelectionIndex();
        if (i >= 0 && i < LESSONS.size()) lessonText.setText(LESSONS.get(i).explanation());
    }

    private void verifyTools() {
        if (!scanning.compareAndSet(false, true)) return;
        readiness.setText("Checking the installed Rust compiler and Cargo...");
        Job job = new Job("Rust beginner guide: verify host tools") {
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    HostToolchain.Report report = HostToolchain.inspect(monitor::isCanceled);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    latest = report;
                    onUi(() -> {
                        readiness.setText(report.readyForCargo()
                                ? "Ready! Rust and Cargo can run host commands. "
                                  + "You do NOT need WinGet or espflash for the first lesson. "
                                  + "Next: create your first Rust project."
                                : "Rust/Cargo needs attention. Choose Setup or repair Rust. "
                                  + "No board drivers are needed for the first PC program.");
                        expertText.setText(report.summary());
                        content.layout(true, true);
                        scroll.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
                    });
                    return Status.OK_STATUS;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return Status.CANCEL_STATUS;
                } catch (Exception error) {
                    onUi(() -> readiness.setText("Host-tool check could not finish: "
                            + error.getMessage() + ". Open Setup & Drivers for details."));
                    return new Status(IStatus.WARNING, Activator.ID,
                            "Rust host scan failed", error);
                } finally {
                    scanning.set(false);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private void openBoardSetup() {
        int selected = boardBox.getSelectionIndex();
        if (selected < 1 || selected > boardList.size()) {
            MessageDialog.openInformation(getSite().getShell(), "Choose a board",
                    "You can learn Rust without a board. Select one above when ready.");
            return;
        }
        try {
            var part = getSite().getPage().showView(SetupCenterView.ID);
            if (part instanceof SetupCenterView setup)
                setup.selectBoard(boardList.get(selected - 1).id());
        } catch (org.eclipse.ui.PartInitException error) {
            MessageDialog.openError(getSite().getShell(), "Board setup",
                    "Could not open setup: " + error.getMessage());
        }
    }

    private void showView(String id) {
        try {
            getSite().getPage().showView(id);
        } catch (org.eclipse.ui.PartInitException error) {
            MessageDialog.openError(getSite().getShell(), "Rust Embedded Workbench",
                    "Could not open view: " + error.getMessage());
        }
    }

    private static List<Board> loadBoards() throws IOException {
        try (InputStream stream = Board.class.getResourceAsStream("/catalog/boards.tsv")) {
            if (stream == null) throw new IOException("Board catalogue unavailable");
            Path temp = Files.createTempFile("rew-guided-boards-", ".tsv");
            try {
                Files.copy(stream, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return BoardRegistry.load(temp).all();
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private void onUi(Runnable update) {
        if (!PlatformUI.isWorkbenchRunning()) return;
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (readiness != null && !readiness.isDisposed()) update.run();
        });
    }

    @Override public void setFocus() {
        if (modeBox != null && !modeBox.isDisposed()) modeBox.setFocus();
    }
}
