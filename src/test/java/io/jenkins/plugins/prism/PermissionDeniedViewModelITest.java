package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link PermissionDeniedViewModel}.
 *
 * @author Akash Manna
 */
class PermissionDeniedViewModelITest extends IntegrationTestWithJenkinsPerTest {
    @Test
    void shouldReturnCorrectDisplayName() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        String fileName = "restricted/source/File.java";
        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, fileName);

        assertThat(viewModel.getDisplayName()).isEqualTo(fileName);
    }

    @Test
    void shouldReturnCorrectOwner() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, "Test.java");

        assertThat(viewModel.getOwner()).isEqualTo(build);
    }

    @Test
    void shouldReturnCorrectFileName() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        String fileName = "src/main/java/Test.java";
        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, fileName);

        assertThat(viewModel.getFileName()).isEqualTo(fileName);
    }

    @Test
    void shouldReturnCorrectPermissionId() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, "Test.java");

        assertThat(viewModel.getRequiredPermission())
                .isEqualTo(SourceCodeViewerPermissions.VIEW_SOURCE_CODE.getId())
                .contains("SourceCodeViewer")
                .contains("ViewSourceCode");
    }

    @Test
    void shouldHandleEmptyFileName() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, "");

        assertThat(viewModel.getFileName()).isEmpty();
        assertThat(viewModel.getDisplayName()).isEmpty();
    }

    @Test
    void shouldHandleSpecialCharactersInFileName() {
        FreeStyleProject project = createFreeStyleProject();
        Run<?, ?> build = buildSuccessfully(project);

        String fileName = "src/test/resources/file with spaces & special-chars.txt";
        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, fileName);

        assertThat(viewModel.getFileName()).isEqualTo(fileName);
        assertThat(viewModel.getDisplayName()).isEqualTo(fileName);
    }
}
