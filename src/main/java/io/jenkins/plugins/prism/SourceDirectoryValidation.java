package io.jenkins.plugins.prism;

import edu.hm.hafner.util.PathUtil;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.IOException;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;

/**
 * Validates the source directories configuration.
 *
 * @author Ullrich Hafner
 */
class SourceDirectoryValidation {
    static final String DIRECTORY_NOT_REGISTERED = "This source directory is not approved yet. Please register this directory in Jenkins' global configuration.";

    private final SourceDirectoryValidator sourceDirectoryValidator;

    /**
     * Creates a new instance of {@link SourceDirectoryValidation}.
     */
    SourceDirectoryValidation() {
        this(new SourceDirectoryValidator());
    }

    @VisibleForTesting
    SourceDirectoryValidation(final SourceDirectoryValidator sourceDirectoryValidator) {
        this.sourceDirectoryValidator = sourceDirectoryValidator;
    }

    /**
     * Performs on-the-fly validation on the source code directory. Checks if relative paths are part of the workspace and
     * that absolute paths are registered.
     *
     * @param project
     *         the project that is configured
     * @param sourceDirectory
     *         the source directory to use
     *
     * @return the validation result
     */
    FormValidation validateSourceDirectory(@CheckForNull final AbstractProject<?, ?> project, final String sourceDirectory) {
        PathUtil pathUtil = new PathUtil();
        if (pathUtil.isAbsolute(sourceDirectory)) {
            if (sourceDirectoryValidator.isAllowedSourceDirectory(sourceDirectory)) {
                return FormValidation.ok();
            }
            return FormValidation.error(DIRECTORY_NOT_REGISTERED);
        }
        if (project != null) { // there is no workspace in pipelines
            try {
                FilePath workspace = project.getSomeWorkspace();
                if (workspace != null && workspace.exists()) {
                    return workspace.validateRelativeDirectory(sourceDirectory);
                }
            }
            catch (InterruptedException | IOException ignore) {
                // ignore and return ok
            }
        }

        return FormValidation.ok();
    }

    static class SourceDirectoryValidator {
        boolean isAllowedSourceDirectory(final String sourceDirectory) {
            return PrismConfiguration.getInstance().isAllowedSourceDirectory(sourceDirectory);
        }
    }
}
