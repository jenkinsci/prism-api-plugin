package io.jenkins.plugins.prism;

import org.junit.jupiter.api.Test;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PrismAppearanceConfiguration}.
 *
 * @author Akash Manna 
 */
class PrismAppearanceConfigurationITest extends IntegrationTestWithJenkinsPerTest {
    @Test
    void shouldProxyPermissionSettingToTheLegacyConfiguration() {
        PrismConfiguration.getInstance().setProtectSourceCodeByPermission(false);

        PrismAppearanceConfiguration configuration = PrismAppearanceConfiguration.getInstance();

        assertThat(configuration.isProtectSourceCodeByPermission()).isFalse();

        configuration.setProtectSourceCodeByPermission(true);

        assertThat(PrismConfiguration.getInstance().isProtectSourceCodeByPermission()).isTrue();
        assertThat(configuration.isProtectSourceCodeByPermission()).isTrue();
    }
}