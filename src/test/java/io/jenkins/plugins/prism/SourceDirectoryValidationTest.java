package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;

import io.jenkins.plugins.util.FormValidationAssert;

import static io.jenkins.plugins.prism.SourceDirectoryValidation.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link SourceDirectoryValidation}.
 *
 * @author Arne Schöntag
 * @author Stephan Plöderl
 * @author Ullrich Hafner
 */
class SourceDirectoryValidationTest {
    @Test
    void shouldValidateRelativeWithoutWorkspace() {
        SourceDirectoryValidation model = createValidation();

        FormValidationAssert.assertThat(model.validateSourceDirectory(null, "skip")).isOk();

        FreeStyleProject job = mock(FreeStyleProject.class);
        FormValidationAssert.assertThat(model.validateSourceDirectory(job, "no-workspace")).isOk();

        FilePath workspace = mock(FilePath.class);
        when(job.getSomeWorkspace()).thenReturn(workspace);
        FormValidationAssert.assertThat(model.validateSourceDirectory(job, "workspace-does-not-exist")).isOk();
    }

    @Test
    void shouldValidateRelativeWithWorkspace() throws IOException, InterruptedException {
        SourceDirectoryValidation model = createValidation();

        FreeStyleProject job = mock(FreeStyleProject.class);
        FilePath workspace = mock(FilePath.class);
        when(job.getSomeWorkspace()).thenReturn(workspace);
        when(workspace.exists()).thenReturn(true);
        String sourceDirectory = "relative";
        when(workspace.validateRelativeDirectory("relative")).thenReturn(FormValidation.ok(sourceDirectory));

        FormValidationAssert.assertThat(model.validateSourceDirectory(job, sourceDirectory))
                .isOk()
                .hasMessage(sourceDirectory);
    }

    @Test
    void shouldIgnoreExceptions() throws IOException, InterruptedException {
        SourceDirectoryValidation model = createValidation();

        FreeStyleProject job = mock(FreeStyleProject.class);
        FilePath workspace = mock(FilePath.class);
        when(workspace.exists()).thenThrow(IOException.class);
        when(job.getSomeWorkspace()).thenReturn(workspace);

        FormValidationAssert.assertThat(model.validateSourceDirectory(job, "sourceDirectory"))
                .isOk();
    }

    @Test
    void shouldValidateWithWorkspace() {
        SourceDirectoryValidator sourceDirectoryValidator = mock(SourceDirectoryValidator.class);
        SourceDirectoryValidation model = new SourceDirectoryValidation(sourceDirectoryValidator);

        String absoluteDirectory = "/absolute";
        FormValidationAssert.assertThat(model.validateSourceDirectory(mock(FreeStyleProject.class), absoluteDirectory))
                .isError()
                .hasMessage(DIRECTORY_NOT_REGISTERED);
        when(sourceDirectoryValidator.isAllowedSourceDirectory(absoluteDirectory)).thenReturn(true);
        FormValidationAssert.assertThat(model.validateSourceDirectory(mock(FreeStyleProject.class), absoluteDirectory))
                .isOk();
    }

    private SourceDirectoryValidation createValidation() {
        return new SourceDirectoryValidation(mock(SourceDirectoryValidator.class));
    }
}
