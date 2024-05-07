package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import jenkins.model.Jenkins;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.JenkinsFacade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link PrismAppearanceConfiguration}.
 *
 * @author Ullrich Hafner
 */
class PrismAppearanceConfigurationTest {
    @Test
    void shouldInitializeThemes() {
        PrismAppearanceConfiguration configuration = createConfiguration();

        assertThat(configuration.getTheme()).isEqualTo(PrismTheme.PRISM);
        configuration.setTheme(PrismTheme.COY);
        assertThat(configuration.getTheme()).isEqualTo(PrismTheme.COY);

        assertThat(configuration.doFillThemeItems()).extracting(o -> o.value).contains(PrismTheme.PRISM.name());
    }

    private PrismAppearanceConfiguration createConfiguration() {
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        when(jenkins.hasPermission(Jenkins.ADMINISTER)).thenReturn(true);
        return new PrismAppearanceConfiguration(mock(GlobalConfigurationFacade.class), jenkins);
    }
}
