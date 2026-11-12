// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IFolderLayout;

/** Native Eclipse perspective for board-first Rust firmware exploration. */
public final class RustPerspective implements IPerspectiveFactory {
    public static final String ID = "io.github.niwantha33.rew.ui.perspective";

    @Override public void createInitialLayout(IPageLayout layout) {
        String editor = layout.getEditorArea();
        IFolderLayout left = layout.createFolder("rustBoardsAndProjects", IPageLayout.LEFT, 0.27f, editor);
        left.addView(StartHereView.ID);
        left.addView(BoardExplorerView.ID);
        left.addView(SetupCenterView.ID);
        left.addView(CargoWorkbenchView.ID);
        left.addView("org.eclipse.ui.navigator.ProjectExplorer");
        layout.addView("org.eclipse.ui.views.ProblemView", IPageLayout.BOTTOM, 0.75f, editor);
        layout.addShowViewShortcut(StartHereView.ID);
        layout.addShowViewShortcut(BoardExplorerView.ID);
        layout.addShowViewShortcut(SetupCenterView.ID);
        layout.addShowViewShortcut(CargoWorkbenchView.ID);
        layout.addPerspectiveShortcut(ID);
    }
}
