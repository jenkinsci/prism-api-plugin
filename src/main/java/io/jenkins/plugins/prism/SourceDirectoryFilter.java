package io.jenkins.plugins.prism;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.PathUtil;

/**
 * Filters source code directories that are not approved in Jenkins' global configuration. A directory is considered
 * safe if it is a sub-folder in the agent workspace. Directories outside the workspace need to be approved by an
 * administrator in Jenkins global configuration page.
 *
 * @author Ullrich Hafner
 * @see PrismConfiguration
 */
public class SourceDirectoryFilter {
    private static final PathUtil PATH_UTIL = new PathUtil();

    /**
     * Filters the specified collection of additional source code directories so that only permitted source directories
     * will be returned. Permitted source directories are absolute paths that have been registered using
     * {@link PrismConfiguration#setSourceDirectories(java.util.List)} or relative paths in the workspace.
     *
     * @param workspacePath
     *         the path to the workspace containing the affected files
     * @param allowedSourceDirectories
     *         the approved source directories from the system configuration section
     * @param requestedSourceDirectories
     *         source directories either as a relative path in the agent workspace or as an absolute path on the agent
     * @param log
     *         logger
     *
     * @return the permitted source directories
     */
    public Set<String> getPermittedSourceDirectories(
            final String workspacePath,
            final Set<String> allowedSourceDirectories,
            final Set<String> requestedSourceDirectories,
            final FilteredLog log) {
        Set<String> filteredDirectories = new HashSet<>();
        for (String sourceDirectory : requestedSourceDirectories) {
            if (isValidDirectory(sourceDirectory)) {
                if (PATH_UTIL.isAbsolute(sourceDirectory)) {
                    verifyAbsoluteDirectory(workspacePath, allowedSourceDirectories, filteredDirectories,
                            PATH_UTIL.getAbsolutePath(sourceDirectory), log
                    );
                }
                else {
                    filteredDirectories.add(PATH_UTIL.createAbsolutePath(workspacePath,
                            sourceDirectory)); // relative workspace paths are always ok
                }
            }
        }
        return filteredDirectories;
    }

    private void verifyAbsoluteDirectory(final String workspacePath, final Set<String> allowedSourceDirectories,
            final Set<String> filteredDirectories, final String sourceDirectory, final FilteredLog log) {
        if (sourceDirectory.equals(workspacePath)) {
            return; // workspace will be checked automatically
        }
        if (sourceDirectory.startsWith(workspacePath)) {
            filteredDirectories.add(PATH_UTIL.getRelativePath(workspacePath, sourceDirectory)); // make path relative to workspace
        }
        else if (allowedSourceDirectories.contains(sourceDirectory)) { // add only registered absolute paths
            filteredDirectories.add(sourceDirectory);
        }
        else {
            log.logError("Removing non-workspace source directory '%s' - "
                    + "it has not been approved in Jenkins' global configuration.", sourceDirectory);
        }
    }

    private boolean isValidDirectory(final String sourceDirectory) {
        return StringUtils.isNotBlank(sourceDirectory) && !"-".equals(sourceDirectory);
    }
}
