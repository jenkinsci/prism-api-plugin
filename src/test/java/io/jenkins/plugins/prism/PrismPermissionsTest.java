package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleProject;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.security.PermissionGroup;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link PrismPermissions}.
 *
 * @author Akash Manna
 */
class PrismPermissionsTest extends IntegrationTestWithJenkinsPerTest {
    @Test
    void shouldHavePermissionGroupAndPermission() {
        PermissionGroup group = PrismPermissions.GROUP;
        Permission permission = PrismPermissions.VIEW_SOURCE_CODE;
        
        assertThat((Object) group).as("Permission group should not be null").isNotNull();
        assertThat(permission).isNotNull();
    }

    @Test
    void shouldHaveCorrectPermissionId() {
        String permissionId = PrismPermissions.VIEW_SOURCE_CODE.getId();
        assertThat(permissionId)
                .contains("Prism")
                .contains("ViewSourceCode");
    }

    @Test
    void shouldAllowAccessForSystemUser() {
        FreeStyleProject project = createFreeStyleProject();
        
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            assertThat(context).isNotNull();
            assertThat(project.hasPermission(PrismPermissions.VIEW_SOURCE_CODE)).isTrue();
        }
    }

    @Test
    void shouldAllowAccessWithReadPermission() {
        FreeStyleProject project = createFreeStyleProject();
        
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            assertThat(context).isNotNull();
            assertThat(PrismPermissions.hasViewSourceCodePermission(project)).isTrue();
        }
    }

    @Test
    void shouldReturnPermissionGroupForPrism() {
        PermissionGroup group = PrismPermissions.GROUP;
        
        assertThat((Object) group).as("Permission group should not be null").isNotNull();
        assertThat(group.getId()).contains("Prism");
    }

    @Test
    void shouldCheckPermissionForProjectParent() {
        FreeStyleProject project = createFreeStyleProject();
        
        boolean hasPermission = PrismPermissions.hasViewSourceCodePermission(project);
        assertThat(hasPermission).isTrue();
    }

    @Test
    void shouldHandleMultiplePermissionChecks() {
        FreeStyleProject project1 = createFreeStyleProject();
        FreeStyleProject project2 = createFreeStyleProject();
        
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            assertThat(context).isNotNull();
            assertThat(PrismPermissions.hasViewSourceCodePermission(project1)).isTrue();
            assertThat(PrismPermissions.hasViewSourceCodePermission(project2)).isTrue();
        }
    }
}
