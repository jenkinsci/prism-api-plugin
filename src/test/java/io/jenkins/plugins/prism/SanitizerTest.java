package io.jenkins.plugins.prism;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import hudson.markup.MarkupFormatter;

import static j2html.TagCreator.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link Sanitizer}.
 *
 * @author Ullrich Hafner
 */
class SanitizerTest {
    @Test
    @Issue("JENKINS-61834")
    void shouldSkipPlus() {
        Sanitizer sanitizer = new Sanitizer();

        assertThat(sanitizer.render("C++")).isEqualTo("C&#43;&#43;");
    }

    @Test
    void shouldGracefullyHandleExceptions() throws IOException {
        Sanitizer sanitizer = new Sanitizer();

        MarkupFormatter formatter = mock(MarkupFormatter.class);
        when(formatter.translate(anyString())).thenThrow(new IOException("Failure"));
        sanitizer.setMarkupFormatter(formatter);

        assertThat(sanitizer.render("C++")).isEqualTo("IOException: Failure");
    }

    @Test
    void shouldEscapeDom() {
        Sanitizer sanitizer = new Sanitizer();

        assertThat(sanitizer.render(div().withText("div")))
                .isEqualTo("<div>div</div>");
        assertThat(sanitizer.render(div().with(
                button().withText("alert").attr("onclick", "alert (\"Evil Code\");"))))
                .isEqualTo("<div>alert</div>");
    }
}
