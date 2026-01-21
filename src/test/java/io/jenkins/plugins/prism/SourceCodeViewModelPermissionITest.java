package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.StringReader;
import java.util.Objects;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;

import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link SourceCodeViewModel} with permission checks.
 *
 * @author Akash Manna
 */
class SourceCodeViewModelPermissionITest extends IntegrationTestWithJenkinsPerTest {
    private static final String TEST_FILE_NAME = "Test.java";
    private static final String TEST_SOURCE_CODE = "public class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}";

    @Test
    void shouldHandleSourceCodeViewPermissions() {
        getJenkins().jenkins.setSecurityRealm(getJenkins().createDummySecurityRealm());

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().toEveryone();
        authStrategy.grant(Item.READ).everywhere().toEveryone();

        getJenkins().jenkins.setAuthorizationStrategy(authStrategy);

        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        var alice = Objects.requireNonNull(User.getById("alice", true));
        try (ACLContext context = ACL.as2(alice.impersonate2())) {
            assertThat(context).isNotNull();

            // By default, alice has no WORKSPACE permission, but the global Prism configuration allows viewing source code without permission
            var view = createView(build);

            assertThat(view).isInstanceOfSatisfying(SourceCodeViewModel.class,
                    sourceCodeView -> {
                        assertThat(sourceCodeView.getDisplayName()).isEqualTo(TEST_FILE_NAME);
                        assertThat(sourceCodeView.getSourceCode()).contains("public class Test");
                        assertThat(sourceCodeView.getOwner()).isEqualTo(build);
                    });

            PrismConfiguration.getInstance().setProtectSourceCodeByPermission(true);

            // Now, alice does not have permission to view source code
            assertThat(createView(build)).isInstanceOf(PermissionDeniedViewModel.class);

            // Grant WORKSPACE permission to Alice, then she should be able to view source code
            authStrategy.grant(Job.WORKSPACE).everywhere().to(alice);

            assertThat(createView(build)).isInstanceOf(SourceCodeViewModel.class);
        }
    }

    private ModelObject createView(final Run<?, ?> build) {
        Marker marker = new MarkerBuilder().withLineStart(1).build();
        try (StringReader reader = new StringReader(TEST_SOURCE_CODE)) {
            return SourceCodeViewModel.create(build, TEST_FILE_NAME, reader, marker);
        }
    }
}
