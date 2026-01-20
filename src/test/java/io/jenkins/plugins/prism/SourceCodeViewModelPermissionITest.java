package io.jenkins.plugins.prism;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;

import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import org.jvnet.hudson.test.MockAuthorizationStrategy;

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
    void shouldCreateSourceCodeViewModelWhenPermissionGranted() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        try (ACLContext context = ACL.as2(ACL.SYSTEM2);
                StringReader reader = new StringReader(TEST_SOURCE_CODE)) {
            assertThat(context).isNotNull();
            ModelObject viewModel = SourceCodeViewModel.create(build, TEST_FILE_NAME, reader, marker);

            assertThat(viewModel).isInstanceOf(SourceCodeViewModel.class);
            SourceCodeViewModel sourceView = (SourceCodeViewModel) viewModel;
            assertThat(sourceView.getDisplayName()).isEqualTo(TEST_FILE_NAME);
            assertThat(sourceView.getSourceCode()).contains("public class Test");
        }
    }

    @Test
    void shouldShowCorrectFileNameInPermissionDeniedView() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        PermissionDeniedViewModel deniedView = new PermissionDeniedViewModel(build, TEST_FILE_NAME);

        assertThat(deniedView.getDisplayName()).isEqualTo(TEST_FILE_NAME);
        assertThat(deniedView.getFileName()).isEqualTo(TEST_FILE_NAME);
        assertThat(deniedView.getOwner()).isEqualTo(build);
        assertThat(deniedView.getRequiredPermission()).isEqualTo(SourceCodeViewerPermissions.VIEW_SOURCE_CODE.getId());
    }

    @Test
    void shouldAllowSystemUserToViewSourceCode() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        try (ACLContext context = ACL.as2(ACL.SYSTEM2);
                StringReader reader = new StringReader(TEST_SOURCE_CODE)) {
            assertThat(context).isNotNull();
            ModelObject viewModel = SourceCodeViewModel.create(build, TEST_FILE_NAME, reader, marker);

            assertThat(viewModel).isInstanceOf(SourceCodeViewModel.class);
        }
    }

    @Test
    void shouldHandleDirectConstructorCall() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        try (StringReader reader = new StringReader(TEST_SOURCE_CODE)) {
            SourceCodeViewModel viewModel = new SourceCodeViewModel(build, TEST_FILE_NAME, reader, marker);

            assertThat(viewModel.getDisplayName()).isEqualTo(TEST_FILE_NAME);
            assertThat(viewModel.getOwner()).isEqualTo(build);
            assertThat(viewModel.getSourceCode()).contains("public class Test");
        }
    }

    @Test
    void shouldRenderSourceCodeWithPrismConfiguration() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        try (StringReader reader = new StringReader(TEST_SOURCE_CODE)) {
            SourceCodeViewModel viewModel = new SourceCodeViewModel(build, TEST_FILE_NAME, reader, marker);

            assertThat(viewModel.getPrismConfiguration()).isNotNull();
            assertThat(viewModel.getPrismConfiguration()).isEqualTo(PrismConfiguration.getInstance());
        }
    }

    @Test
    void shouldCreateViewModelWithSystemUser() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        try (ACLContext context = ACL.as2(ACL.SYSTEM2);
                StringReader reader1 = new StringReader(TEST_SOURCE_CODE)) {
            assertThat(context).isNotNull();
            ModelObject viewModel1 = SourceCodeViewModel.create(build, TEST_FILE_NAME, reader1, marker);
            assertThat(viewModel1).isInstanceOf(SourceCodeViewModel.class);
        }

        try (ACLContext context = ACL.as2(ACL.SYSTEM2);
                StringReader reader2 = new StringReader(TEST_SOURCE_CODE)) {
            assertThat(context).isNotNull();
            ModelObject viewModel2 = SourceCodeViewModel.create(build, TEST_FILE_NAME, reader2, marker);
            assertThat(viewModel2).isInstanceOf(SourceCodeViewModel.class);
        }
    }

    @Test
    void shouldHandleEmptySourceCode() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        try (StringReader reader = new StringReader("")) {
            SourceCodeViewModel viewModel = new SourceCodeViewModel(build, "Empty.java", reader, marker);

            assertThat(viewModel.getSourceCode()).isNotNull();
        }
    }

    @Test
    void shouldReturnPermissionDeniedViewModelWhenPermissionDenied() {
        // Set up security with MockAuthorizationStrategy where alice has no
        // VIEW_SOURCE_CODE permission
        getJenkins().jenkins.setSecurityRealm(getJenkins().createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().toEveryone();
        authStrategy.grant(Item.READ).everywhere().toEveryone();
        // Do not grant VIEW_SOURCE_CODE to alice - the permission is opt-out, so we
        // need to explicitly deny it
        getJenkins().jenkins.setAuthorizationStrategy(authStrategy);

        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        Marker marker = new MarkerBuilder().withLineStart(1).build();

        // Test with alice who doesn't have VIEW_SOURCE_CODE permission
        try (ACLContext context = ACL.as2(hudson.model.User.getById("alice", true).impersonate2());
                StringReader reader = new StringReader(TEST_SOURCE_CODE)) {
            assertThat(context).isNotNull();
            ModelObject viewModel = SourceCodeViewModel.create(build, TEST_FILE_NAME, reader, marker);

            assertThat(viewModel).isInstanceOf(PermissionDeniedViewModel.class);
            PermissionDeniedViewModel deniedView = (PermissionDeniedViewModel) viewModel;
            assertThat(deniedView.getFileName()).isEqualTo(TEST_FILE_NAME);
            assertThat(deniedView.getRequiredPermission())
                    .isEqualTo(SourceCodeViewerPermissions.VIEW_SOURCE_CODE.getId());
        }
    }
}
