// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import io.github.niwantha33.rew.cargo.CargoDiagnostics;
import io.github.niwantha33.rew.cargo.CargoProject;

/**
 * Owns only REW-generated problem markers. Never deletes markers from other
 * Eclipse tools or reports cross-project diagnostics without path evidence.
 */
final class CargoProblems {
    private CargoProblems() {}

    static int publish(CargoProject cargo, List<CargoDiagnostics.Diagnostic> diagnostics)
            throws CoreException {
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            IFile ordinary = project.getFile("Cargo.toml");
            IFile linked = project.getFolder("source").getFile("Cargo.toml");
            IFile manifest = ordinary.exists() ? ordinary : linked;
            if (!manifest.exists() || manifest.getLocation() == null) continue;
            Path path = manifest.getLocation().toFile().toPath().toAbsolutePath().normalize();
            if (!path.equals(cargo.manifest())) continue;
            String prefix = ordinary.exists() ? "" : "source/";
            int[] marked = {0};
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            workspace.run(monitor -> {
                for (IMarker old : project.findMarkers(IMarker.PROBLEM, true,
                        IResource.DEPTH_INFINITE)) {
                    if (Activator.ID.equals(old.getAttribute(IMarker.SOURCE_ID, "")))
                        old.delete();
                }
                for (CargoDiagnostics.Diagnostic diagnostic : diagnostics) {
                    Path local = cargo.directory().relativize(diagnostic.file());
                    IFile file = project.getFile(prefix
                            + local.toString().replace(File.separatorChar, '/'));
                    if (!file.exists()) continue;
                    IMarker marker = file.createMarker(IMarker.PROBLEM);
                    marker.setAttribute(IMarker.SOURCE_ID, Activator.ID);
                    marker.setAttribute(IMarker.SEVERITY,
                            diagnostic.severity() == CargoDiagnostics.Severity.ERROR
                                    ? IMarker.SEVERITY_ERROR : IMarker.SEVERITY_WARNING);
                    marker.setAttribute(IMarker.LINE_NUMBER, diagnostic.line());
                    marker.setAttribute(IMarker.LOCATION, diagnostic.file().toString()
                            + ":" + diagnostic.line() + ":" + diagnostic.column());
                    marker.setAttribute(IMarker.MESSAGE, diagnostic.message());
                    marked[0]++;
                }
            }, project, IWorkspace.AVOID_UPDATE, null);
            return marked[0];
        }
        return 0;
    }
}
