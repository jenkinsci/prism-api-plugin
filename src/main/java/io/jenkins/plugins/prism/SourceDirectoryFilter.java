package io.jenkins.plugins.prism;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        var normalizedWorkspacePath = PATH_UTIL.getAbsolutePath(workspacePath);
        Set<String> filteredDirectories = new HashSet<>();
        for (String sourceDirectory : requestedSourceDirectories) {
            if (isValidDirectory(sourceDirectory)) {
                if (PATH_UTIL.isAbsolute(sourceDirectory) && containsNoPathMatcherPattern(sourceDirectory)) {
                    verifyAbsoluteDirectory(normalizedWorkspacePath, allowedSourceDirectories, filteredDirectories,
                            PATH_UTIL.getAbsolutePath(sourceDirectory), log);
                }
                else { // relative workspace paths are always ok
                    filteredDirectories.addAll(findRelative(normalizedWorkspacePath, sourceDirectory, log));
                }
            }
        }
        return filteredDirectories;
    }

    private void verifyAbsoluteDirectory(final String workspacePath, final Set<String> allowedSourceDirectories,
            final Set<String> filteredDirectories, final String sourceDirectory, final FilteredLog log) {
        var normalizedSourceDirectory = PATH_UTIL.getAbsolutePath(sourceDirectory);
        if (normalizedSourceDirectory.equals(workspacePath)) {
            return; // workspace will be checked automatically
        }
        if (normalizedSourceDirectory.startsWith(workspacePath)) {
            filteredDirectories.add(PATH_UTIL.getRelativePath(workspacePath,
                    normalizedSourceDirectory)); // make path relative to workspace
        }
        else if (allowedSourceDirectories.contains(normalizedSourceDirectory)) { // add only registered absolute paths
            filteredDirectories.add(normalizedSourceDirectory);
        }
        else {
            log.logError("Removing non-workspace source directory '%s' - "
                    + "it has not been approved in Jenkins' global configuration.", normalizedSourceDirectory);
        }
    }

    private boolean isValidDirectory(final String sourceDirectory) {
        return StringUtils.isNotBlank(sourceDirectory) && !"-".equals(sourceDirectory);
    }

    /**
     * Returns the subdirectories of a given base directory that match a specified pattern.
     *
     * @param directory
     *         the directory where to search for files
     * @param pattern
     *         the pattern to use when searching
     * @param log
     *         the logger
     *
     * @return the matching paths
     * @see FileSystem#getPathMatcher(String)
     */
    private List<String> findRelative(final String directory, final String pattern, final FilteredLog log) {
        if (containsNoPathMatcherPattern(pattern)) {
            return List.of(PATH_UTIL.createAbsolutePath(directory, pattern));
        }

        try {
            PathMatcherFileVisitor visitor = new PathMatcherFileVisitor(pattern);
            Files.walkFileTree(Paths.get(directory), visitor);
            return visitor.getMatches();
        }
        catch (IllegalArgumentException exception) {
            log.logException(exception,
                    "Pattern not valid for FileSystem.getPathMatcher: '%s'", pattern);
        }
        catch (IOException exception) {
            log.logException(exception,
                    "Cannot find subdirectories in '%s' for glob: pattern '%s'", directory, pattern);
        }

        return new ArrayList<>();
    }

    private boolean containsNoPathMatcherPattern(final String pattern) {
        return !pattern.startsWith("glob:") && !pattern.startsWith("regex:");
    }

    private static class PathMatcherFileVisitor extends SimpleFileVisitor<Path> {
        private final PathMatcher pathMatcher;
        private final List<String> matches = new ArrayList<>();

        PathMatcherFileVisitor(final String syntaxAndPattern) {
            super();

            pathMatcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
        }

        List<String> getMatches() {
            return matches;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            if (pathMatcher.matches(dir)) {
                matches.add(PATH_UTIL.getAbsolutePath(dir));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }
}
