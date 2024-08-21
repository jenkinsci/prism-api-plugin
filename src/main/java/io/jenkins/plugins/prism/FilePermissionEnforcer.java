package io.jenkins.plugins.prism;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import edu.hm.hafner.util.PathUtil;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.FilePath;

/**
 * Enforces security restrictions for viewing files in Jenkins. Some plugins copy source code files to Jenkins' build
 * folder so that these files can be rendered in the user interface together with build results (coverage, warnings,
 * etc.). If these files are not part of the workspace of a build, then Jenkins will not show them by default: otherwise
 * sensitive files could be shown by accident. You can provide a list of additional source code directories that are
 * allowed to be shown in Jenkins user interface here. Note, that such a directory must be an absolute path on the
 * <b>agent</b> that executes the build.
 *
 * @author Ullrich Hafner
 */
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "We are checking if a file is in a workspace")
public class FilePermissionEnforcer {
    private static final PathUtil PATH_UTIL = new PathUtil();

    /**
     * Checks whether the specified file is part of Jenkins' workspace or one of the permitted directories.
     *
     * @param fileName
     *         the file name of the source
     * @param workspace
     *         the workspace on the agent, files within that folder are always permitted
     * @param permittedDirectories
     *         additional permitted directories
     *
     * @return {@code true} if the file is in the workspace, {@code false} otherwise
     */
    public boolean isInWorkspace(final String fileName, final FilePath workspace, final String... permittedDirectories) {
        return isInWorkspace(fileName, workspace, new HashSet<>(Arrays.asList(permittedDirectories)));
    }

    /**
     * Checks whether the specified file is part of Jenkins' workspace or one of the permitted directories.
     *
     * @param fileName
     *         the file name of the source
     * @param workspace
     *         the workspace on the agent, files within that folder are always permitted
     * @param permittedDirectories
     *         an additional set of permitted directories
     *
     * @return {@code true} if the file is in the workspace, {@code false} otherwise
     */
    public boolean isInWorkspace(final String fileName, final FilePath workspace, final Set<String> permittedDirectories) {
        String sourceFile = PATH_UTIL.getAbsolutePath(fileName);
        Set<String> permittedAbsolutePaths = permittedDirectories.stream()
                .map(PATH_UTIL::getAbsolutePath)
                .collect(Collectors.toSet());
        permittedAbsolutePaths.add(workspace.getRemote());
        permittedAbsolutePaths.add(resolveWorkspace(workspace));

        return permittedAbsolutePaths.stream()
                .map(Paths::get)
                .anyMatch(prefix -> Paths.get(sourceFile).startsWith(prefix));
    }

    @VisibleForTesting
    String resolveWorkspace(final FilePath workspace) {
        try {
            var resolved = workspace.readLink();
            if (resolved != null) {
                return resolved;
            }
        }
        catch (IOException | InterruptedException ignore) {
            // ignore
        }
        return workspace.getRemote();
    }
}
