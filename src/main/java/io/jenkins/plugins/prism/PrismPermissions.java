package io.jenkins.plugins.prism;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.model.Item;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;

/**
 * Defines permissions for the Prism plugin related to source code viewing.
 * This permission is opt-out by default, meaning users have permission by default,
 * and administrators can explicitly restrict it.
 *
 * @author Akash Manna
 */
public final class PrismPermissions {
    /**
     * Permission group for Prism-related permissions.
     */
    public static final PermissionGroup GROUP = new PermissionGroup(
            PrismPermissions.class,
            Messages._PrismPermissions_GroupTitle()
    );

    /**
     * Permission to view source code in the Prism viewer.
     * This is an opt-out permission: by default, users have this permission,
     * and administrators can explicitly restrict it via Jenkins security configuration.
     */
    public static final Permission VIEW_SOURCE_CODE = new Permission(
            GROUP,
            "ViewSourceCode",
            Messages._PrismPermissions_ViewSourceCode_Description(),
            Item.READ,
            true, // opt-out: enabled by default
            new PermissionScope[]{PermissionScope.ITEM}
    );

    private PrismPermissions() {
        // utility class
    }

    /**
     * Checks if the current user has permission to view source code for the given item.
     *
     * @param item the Jenkins item (job, build, etc.) to check permission for
     * @return {@code true} if the user has permission to view source code, {@code false} otherwise
     */
    public static boolean hasViewSourceCodePermission(@NonNull final Item item) {
        return item.hasPermission(VIEW_SOURCE_CODE);
    }
}
