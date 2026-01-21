package io.jenkins.plugins.prism;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import hudson.model.Run;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link PermissionDeniedViewModel}.
 *
 * @author Akash Manna
 */
class PermissionDeniedViewModelTest {
    @ParameterizedTest(name = "Should return correct display name for file name: {0}")
    @ValueSource(strings = {
            "restricted/source/File.java",
            "",
            "file with spaces & special-chars.txt"
    })
    void shouldReturnCorrectDisplayName(final String fileName) {
        Run<?, ?> build = mock(Run.class);

        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, fileName);

        assertThat(viewModel.getOwner()).isEqualTo(build);
        assertThat(viewModel.getDisplayName()).isEqualTo(fileName);
        assertThat(viewModel.getFileName()).isEqualTo(fileName);
        assertThat(viewModel.getRequiredPermission()).contains("Item.Workspace");
    }
}
