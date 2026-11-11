// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;

import io.github.niwantha33.rew.core.Board;
import io.github.niwantha33.rew.core.BoardRegistry;

/** Read-only board browser; catalog candidates are NOT validated hardware. */
public final class BoardExplorerView extends ViewPart {
    public static final String ID = "io.github.niwantha33.rew.ui.boardExplorer";
    private TableViewer viewer;
    private Text search;
    private Text details;
    private List<Board> all = List.of();

    @Override public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));
        var heading = new Label(parent, SWT.WRAP);
        heading.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        heading.setText("Candidate Rust boards — documentation metadata, NOT build/flash/debug qualification.");
        search = new Text(parent, SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        search.setMessage("Filter by vendor, board, chip, or Rust target");
        search.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL);
        Table table = viewer.getTable();
        table.setLinesVisible(true);
        table.setHeaderVisible(true);
        var tableLayout = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableLayout.heightHint = 160;
        table.setLayoutData(tableLayout);
        column("Vendor", 145, Board::vendor);
        column("Board", 180, Board::board);
        column("MCU", 120, Board::mcu);
        column("Target", 205, Board::rustTarget);
        column("Evidence", 120, board -> board.evidence().toString());
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        details = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData detailLayout = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailLayout.heightHint = 145;
        details.setLayoutData(detailLayout);
        viewer.addSelectionChangedListener(event -> showSelection());
        search.addModifyListener(event -> filter());

        try {
            all = loadPackagedBoardCatalog();
            filter();
        } catch (Exception e) {
            details.setText("Could not load the bundled, validated board catalog: " + e.getMessage());
            Activator.getDefault().getLog().warn("Rust board catalog unavailable: " + e.getMessage());
        }
    }

    private void column(String name, int width, java.util.function.Function<Board, String> value) {
        var column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(name);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return value.apply((Board) element);
            }
        });
    }

    private void filter() {
        String term = search.getText().strip().toLowerCase(java.util.Locale.ROOT);
        List<Board> displayed = all.stream().filter(b -> term.isBlank()
                || (b.vendor() + " " + b.board() + " " + b.mcu() + " " + b.rustTarget())
                    .toLowerCase(java.util.Locale.ROOT).contains(term)).toList();
        viewer.setInput(displayed);
        details.setText("Select a board to see its source, toolchain and evidence status. "
                + "This list is not a verified flashing or debugging compatibility matrix.");
    }

    private void showSelection() {
        if (!(viewer.getStructuredSelection() instanceof IStructuredSelection selection)
                || !(selection.getFirstElement() instanceof Board b)) return;
        details.setText("Board: " + b.board() + "\n"
                + "Vendor / MCU: " + b.vendor() + " / " + b.mcu() + "\n"
                + "Rust target: " + b.rustTarget() + " (" + b.toolchain() + ")\n"
                + "HAL candidate: " + b.framework() + "\n"
                + "Debug candidate (NOT verified): " + b.debugCandidate() + "\n"
                + "Evidence: " + b.evidence() + "\n"
                + "Reference: " + b.upstreamSource() + "\n"
                + "Notes: " + b.notes());
    }

    private static List<Board> loadPackagedBoardCatalog() throws IOException {
        // The exact same catalog bytes are checked against root/catalog in CI.
        try (InputStream stream = Board.class.getResourceAsStream("/catalog/boards.tsv")) {
            if (stream == null) throw new IOException("Missing bundled catalog/boards.tsv");
            Path temporary = Files.createTempFile("rew-catalog-", ".tsv");
            try {
                Files.copy(stream, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return BoardRegistry.load(temporary).all();
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Override public void setFocus() {
        if (search != null && !search.isDisposed()) search.setFocus();
    }
}
