package io.jenkins.plugins.prism;

import hudson.model.ModelObject;
import hudson.model.Run;

/**
 * View model that is shown when a user does not have permission to view source code.
 *
 * @author Akash Manna
 */
public class PermissionDeniedViewModel implements ModelObject {
    private final Run<?, ?> owner;
    private final String fileName;

    /**
     * Creates a new instance of {@link PermissionDeniedViewModel}.
     *
     * @param owner
     *         the current build as owner of this view
     * @param fileName
     *         the file name that was requested
     */
    public PermissionDeniedViewModel(final Run<?, ?> owner, final String fileName) {
        this.owner = owner;
        this.fileName = fileName;
    }

    @Override
    public String getDisplayName() {
        return fileName;
    }

    /**
     * Returns the build as owner of this view.
     *
     * @return the build
     */
    public Run<?, ?> getOwner() {
        return owner;
    }

    /**
     * Returns the requested file name.
     *
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the permission required to view source code.
     *
     * @return the permission name
     */
    public String getRequiredPermission() {
        return PrismPermissions.VIEW_SOURCE_CODE.getId();
    }
}
