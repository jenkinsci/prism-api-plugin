package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleProject;
import hudson.security.ACL;
import hudson.security.ACLContext;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link SourceCodeViewerPermissions}.
 *
 * @author Akash Manna
 */
class SourceCodeViewerPermissionsTest extends IntegrationTestWithJenkinsPerTest {
    @Test
    void shouldAllowAccessForSystemUser() {
        FreeStyleProject project = createFreeStyleProject();
        
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            assertThat(context).isNotNull();
            assertThat(SourceCodeViewerPermissions.hasViewSourceCodePermission(project)).isTrue();
        }
    }
}
