package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import hudson.model.Run;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link PermissionDeniedViewModel}.
 *
 * @author Akash Manna
 */
class PermissionDeniedViewModelITest {
    @Test
    void shouldReturnCorrectDisplayName() {
        Run<?, ?> build = mock(Run.class);

        String fileName = "restricted/source/File.java";
        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, fileName);

        assertThat(viewModel.getOwner()).isEqualTo(build);
        assertThat(viewModel.getDisplayName()).isEqualTo(fileName);
        assertThat(viewModel.getFileName()).isEqualTo(fileName);
    }

    @Test
    void shouldHandleEmptyFileName() {
        Run<?, ?> build = mock(Run.class);

        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, "");

        assertThat(viewModel.getFileName()).isEmpty();
        assertThat(viewModel.getDisplayName()).isEmpty();
    }

    @Test
    void shouldHandleSpecialCharactersInFileName() {
        Run<?, ?> build = mock(Run.class);

        String fileName = "src/test/resources/file with spaces & special-chars.txt";
        PermissionDeniedViewModel viewModel = new PermissionDeniedViewModel(build, fileName);

        assertThat(viewModel.getFileName()).isEqualTo(fileName);
        assertThat(viewModel.getDisplayName()).isEqualTo(fileName);
    }
}
