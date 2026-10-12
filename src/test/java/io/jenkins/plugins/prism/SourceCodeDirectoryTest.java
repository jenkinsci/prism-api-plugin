package io.jenkins.plugins.prism;

import static org.assertj.core.api.Assertions.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link SourceCodeDirectory}.
 *
 * @author Ullrich Hafner
 */
class SourceCodeDirectoryTest {
    private static final String DIRECTORY = "one";

    @Test
    void shouldObeyEqualsContract() {
        EqualsVerifier.forClass(SourceCodeDirectory.class).verify();
    }

    @Test
    void shouldCreateInstance() {
        SourceCodeDirectory directory = new SourceCodeDirectory(DIRECTORY);

        assertThat(directory.getPath()).isEqualTo(DIRECTORY);
    }
}
