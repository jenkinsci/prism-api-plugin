package io.jenkins.plugins.prism;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.model.Item;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;

/**
 * Defines permissions related to source code viewing.
 * This permission is opt-out by default, meaning users have permission by default,
 * and administrators can explicitly restrict it.
 *
 * @author Akash Manna
 */
final class SourceCodeViewerPermissions {
    static final String ID = "ViewSourceCode";

    /** Permission group for source code viewer permissions. */
    static final PermissionGroup GROUP = new PermissionGroup(
            SourceCodeViewerPermissions.class,
            Messages._SourceCodeViewerPermissions_GroupTitle()
    );

    /**
     * Permission to view source code.
     * This is an opt-out permission: by default, users have this permission,
     * and administrators can explicitly restrict it via Jenkins security configuration.
     */
    static final Permission VIEW_SOURCE_CODE = new Permission(
            GROUP,
            ID,
            Messages._SourceCodeViewerPermissions_ViewSourceCode_Description(),
            Item.READ,
            true, // opt-out: enabled by default
            new PermissionScope[]{PermissionScope.ITEM}
    );

    /**
     * Checks if the current user has permission to view source code for the given item.
     *
     * @param item the Jenkins item (job, build, etc.) to check permission for
     * @return {@code true} if the user has permission to view source code, {@code false} otherwise
     */
    static boolean hasViewSourceCodePermission(@NonNull final Item item) {
        return item.hasPermission(VIEW_SOURCE_CODE);
    }

    private SourceCodeViewerPermissions() {
        // utility class
    }
}
