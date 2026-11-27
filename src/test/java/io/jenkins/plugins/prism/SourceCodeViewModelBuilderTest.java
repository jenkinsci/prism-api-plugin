package io.jenkins.plugins.prism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import hudson.model.Run;
import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link SourceCodeViewModel.Builder}.
 *
 * @author Ullrich Hafner
 */
class SourceCodeViewModelBuilderTest {
    private static final String FILE_NAME = "Console Output";

    /**
     * Verifies that an excerpt of the lines is shown as plain text, without checking the permission to view the
     * source code of the workspace.
     */
    @Test
    void shouldShowExcerptAsPlainText() {
        Run<?, ?> build = mock(Run.class);
        var marker = new MarkerBuilder().withLineStart(3).withTitle("Warning").build();

        var view = new SourceCodeViewModel.Builder(build, FILE_NAME)
                .withMarkers(List.of(marker))
                .withPlainText()
                .withExcerpt(2, 4)
                .withoutPermissionCheck()
                .build(Stream.of("one", "two", "three", "four", "five", "six"));

        assertThat(view).isInstanceOfSatisfying(SourceCodeViewModel.class, model -> {
            assertThat(model.getDisplayName()).isEqualTo(FILE_NAME);
            assertThat(model.getOwner()).isSameAs(build);
            assertThat(model.isCollapsible()).isTrue();

            var code = Objects.requireNonNull(Jsoup.parse(model.getSourceCode())
                    .select(".prism-source__raw > code")
                    .first());
            assertThat(code.wholeText()).isEqualTo("two\nthree\nfour");
            assertThat(code.attr("data-first-line")).isEqualTo("2");
            assertThat(code.classNames()).isEmpty();
            assertThat(Jsoup.parse(model.getSourceCode())
                            .getElementsByClass("prism-marker__title")
                            .text())
                    .isEqualTo("Warning");
        });
    }

    /**
     * Verifies that the whole content of a reader is shown if no excerpt is defined.
     */
    @Test
    void shouldShowWholeContentOfReader() {
        Run<?, ?> build = mock(Run.class);

        var view = new SourceCodeViewModel.Builder(build, "Hello.java")
                .withoutPermissionCheck()
                .build(new StringReader("class Hello {\n}\n"));

        assertThat(view).isInstanceOfSatisfying(SourceCodeViewModel.class, model -> {
            var code = Objects.requireNonNull(Jsoup.parse(model.getSourceCode())
                    .select(".prism-source__raw > code")
                    .first());
            assertThat(code.wholeText()).isEqualTo("class Hello {\n}");
            assertThat(code.attr("data-first-line")).isEqualTo("1");
            assertThat(code.classNames()).containsExactly("language-java");
        });
    }

    /**
     * Verifies that collapsing of the lines without markers can be disabled.
     */
    @Test
    void shouldDisableCollapsing() {
        var view = new SourceCodeViewModel.Builder(mock(Run.class), FILE_NAME)
                .withoutCollapsing()
                .withoutPermissionCheck()
                .build(Stream.of("one", "two"));

        assertThat(view)
                .isInstanceOfSatisfying(
                        SourceCodeViewModel.class,
                        model -> assertThat(model.isCollapsible()).isFalse());
    }

    /**
     * Verifies that the annotations of the lines of an excerpt are shown, the annotations of the other lines are
     * skipped.
     */
    @Test
    void shouldShowAnnotationsOfExcerpt() {
        var view = new SourceCodeViewModel.Builder(mock(Run.class), FILE_NAME)
                .withLineAnnotations(
                        List.of(new LineAnnotation(1, "skipped"), new LineAnnotation(3, "2/4", "Covered branches")))
                .withExcerpt(2, 4)
                .withoutPermissionCheck()
                .build(Stream.of("one", "two", "three", "four", "five"));

        assertThat(view).isInstanceOfSatisfying(SourceCodeViewModel.class, model -> {
            var annotations = Jsoup.parse(model.getSourceCode()).getElementsByClass("prism-line__annotation");
            assertThat(annotations)
                    .extracting(e -> e.attr("data-line"), Element::text, e -> e.attr("tooltip"))
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("3", "2/4", "Covered branches"));
        });
    }

    /**
     * Verifies that an excerpt after the end of the content is empty.
     */
    @Test
    void shouldShowEmptyExcerpt() {
        var view = new SourceCodeViewModel.Builder(mock(Run.class), FILE_NAME)
                .withExcerpt(10, 20)
                .withoutPermissionCheck()
                .build(Stream.of("one", "two"));

        assertThat(view).isInstanceOfSatisfying(SourceCodeViewModel.class, model -> {
            var code = Objects.requireNonNull(Jsoup.parse(model.getSourceCode())
                    .select(".prism-source__raw > code")
                    .first());
            assertThat(code.wholeText()).isEmpty();
            assertThat(code.attr("data-lines")).isEqualTo("0");
        });
    }
}
