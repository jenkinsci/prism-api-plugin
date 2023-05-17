package io.jenkins.plugins.prism;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.PathUtil;

import jenkins.model.Jenkins;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.JenkinsFacade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link PrismConfiguration}.
 *
 * @author Ullrich Hafner
 */
class PrismConfigurationTest {
    private static final PathUtil PATH_UTIL = new PathUtil();

    private static final String FIRST = "/One";
    private static final String SECOND = "/Two";
    private static final String ABSOLUTE_NOT_EXISTING = "/Three";
    private static final String RELATIVE = "Relative";

    private static final String NORMALIZED = PATH_UTIL.getAbsolutePath("/workspace");

    private static final List<PermittedSourceCodeDirectory> SOURCE_ROOTS
            = Arrays.asList(new PermittedSourceCodeDirectory(FIRST), new PermittedSourceCodeDirectory(SECOND));
    private static final SourceDirectoryFilter FILTER = new SourceDirectoryFilter();

    @Test
    void shouldHaveNoRootFoldersWhenCreated() {
        PrismConfiguration configuration = createConfiguration();

        assertThat(configuration.getSourceDirectories()).isEmpty();

        FilteredLog log = new FilteredLog("Error");
        assertThat(get(configuration, log, "")).isEmpty();
        assertThat(get(configuration, log, "-")).isEmpty();
        assertThat(get(configuration, log, RELATIVE)).containsExactly(getWorkspaceChild(RELATIVE));
        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(get(configuration, log, ABSOLUTE_NOT_EXISTING)).isEmpty();
        assertThat(log.getErrorMessages())
                .anySatisfy(m -> assertThat(m).contains("Removing").contains("'/Three'"));
    }

    @Test
    void shouldSaveConfigurationIfFoldersAreAdded() {
        GlobalConfigurationFacade facade = mock(GlobalConfigurationFacade.class);
        PrismConfiguration configuration = new PrismConfiguration(facade, mock(JenkinsFacade.class));

        configuration.setSourceDirectories(SOURCE_ROOTS);

        verify(facade).save();
        assertThat(configuration.getSourceDirectories()).isEqualTo(SOURCE_ROOTS);

        FilteredLog log = new FilteredLog("Error");
        assertThat(get(configuration, log, "")).isEmpty();
        assertThat(get(configuration, log, "-")).isEmpty();
        assertThat(get(configuration, log, FIRST)).containsExactly(FIRST);
        assertThat(get(configuration, log, RELATIVE)).containsExactly(getWorkspaceChild(RELATIVE));
        assertThat(get(configuration, log, RELATIVE, FIRST)).containsExactlyInAnyOrder(FIRST, getWorkspaceChild(RELATIVE));
        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(get(configuration, log, ABSOLUTE_NOT_EXISTING)).isEmpty();
        assertThat(log.getErrorMessages())
                .anySatisfy(m -> assertThat(m).contains("Removing").contains("'/Three'"));
        assertThat(get(configuration, log, ABSOLUTE_NOT_EXISTING, FIRST)).containsExactly(FIRST);

        configuration.clearRepeatableProperties();
        assertThat(configuration.getSourceDirectories()).isEmpty();
    }

    @Test
    void shouldNormalizePath() {
        PrismConfiguration configuration = createConfiguration();

        configuration.setSourceDirectories(
                Arrays.asList(new PermittedSourceCodeDirectory("/absolute/unix"),
                        new PermittedSourceCodeDirectory("C:\\absolute\\windows")));

        String relativeUnix = "relative/unix";
        String relativeWindows = "relative\\windows";
        String absoluteUnix = "/absolute/unix";
        String absoluteWindows = "C:\\absolute\\windows";
        String absoluteWindowsNormalized = "C:/absolute/windows";

        FilteredLog log = new FilteredLog("Error");
        assertThat(get(configuration, log, relativeUnix)).containsExactly(getWorkspaceChild(relativeUnix));
        assertThat(get(configuration, log, relativeWindows)).containsExactly(getWorkspaceChild(relativeWindows));
        assertThat(get(configuration, log, absoluteUnix)).containsExactly(absoluteUnix);
        assertThat(get(configuration, log, absoluteWindows)).containsExactly(normalize(absoluteWindows));
        assertThat(get(configuration, log, absoluteWindowsNormalized)).containsExactly(absoluteWindowsNormalized);
        assertThat(log.getErrorMessages()).isEmpty();
    }

    @Test
    void shouldInitializeThemes() {
        PrismConfiguration configuration = createConfiguration();

        assertThat(configuration.getTheme())
                .isEqualTo(PrismTheme.PRISM)
                .extracting(PrismTheme::getFileName)
                .isEqualTo("prism.css");
        configuration.setTheme(PrismTheme.COY);
        assertThat(configuration.getTheme()).isEqualTo(PrismTheme.COY);

        assertThat(configuration.doFillThemeItems()).extracting(o -> o.value).contains(PrismTheme.PRISM.name());
    }

    private String getWorkspaceChild(final String expected) {
        return PATH_UTIL.createAbsolutePath(NORMALIZED, expected);
    }

    private String normalize(final String remote) {
        return PATH_UTIL.getAbsolutePath(remote);
    }

    private List<String> get(final PrismConfiguration configuration, final FilteredLog log,
            final String... absolutePaths) {
        Set<String> sourceDirectories = configuration.getSourceDirectories()
                .stream()
                .map(PermittedSourceCodeDirectory::getPath)
                .map(PATH_UTIL::getAbsolutePath)
                .collect(Collectors.toSet());
        return FILTER.getPermittedSourceDirectories(NORMALIZED, sourceDirectories, asSet(absolutePaths), log)
                .stream()
                .map(this::normalize)
                .collect(Collectors.toList());
    }

    private Set<String> asSet(final String... absolutePaths) {
        return new HashSet<>(Arrays.asList(absolutePaths));
    }

    private PrismConfiguration createConfiguration() {
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        when(jenkins.hasPermission(Jenkins.ADMINISTER)).thenReturn(true);
        return new PrismConfiguration(mock(GlobalConfigurationFacade.class), jenkins);
    }
}
