package io.jenkins.plugins.prism;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.ResourceTest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import io.jenkins.plugins.util.JenkinsFacade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link SourcePrinter}.
 *
 * @author Ullrich Hafner
 */
class SourcePrinterTest extends ResourceTest {
    private static final String MESSAGE = "Hello Message";
    private static final String DESCRIPTION = "Hello Description";
    private static final String FILE_NAME = "filename.txt";
    private static final String ICON = "/plugin/xyz/icon";

    @Test
    void shouldCreateSourceWithoutLineNumber() {
        MarkerBuilder builder = new MarkerBuilder();
        SourcePrinter printer = new SourcePrinter();

        Marker issue = builder.build();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));
        String expectedFile = toString("format-java.txt");

        assertThat(document.text()).isEqualToIgnoringWhitespace(expectedFile);

        Elements pre = document.getElementsByTag("pre");
        assertThat(pre.text()).isEqualToIgnoringWhitespace(expectedFile);
    }

    @Test
    void shouldCreateSourceWithLineNumber() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(7).withTitle(MESSAGE).withDescription(DESCRIPTION).build();

        SourcePrinter printer = new SourcePrinter(createJenkinsFacade());

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("analysis-warning-title").text())
                .isEqualTo(MESSAGE);
        assertThat(document.getElementsByClass("analysis-detail").text())
                .isEqualTo(DESCRIPTION);
    }

    private void assertThatCodeIsEqualToSourceText(final Document document) {
        Elements code = document.getElementsByTag("code");
        assertThat(code.text()).isEqualToIgnoringWhitespace(toString("format-java.txt"));
    }

    @Test
    void shouldCreateSourceWithoutDescription() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(7).withTitle("Hello Message").build();

        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("analysis-warning-title").text())
                .isEqualTo(MESSAGE);
        assertThat(document.getElementsByClass("analysis-detail")).isEmpty();
        assertThat(document.getElementsByClass("collapse-panel")).isEmpty();
    }

    @Test
    void shouldCreateIcon() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(7).withTitle("Hello Message").withIcon(ICON).build();

        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        when(jenkins.getImagePath(ICON)).thenReturn("/resolved");
        SourcePrinter printer = new SourcePrinter(jenkins);

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("analysis-title").html()).contains("<td><img src=\"/resolved\" class=\"icon-md\"></td>");
    }

    @Test
    void shouldFilterTagsInCode() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(2).build();

        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-jelly.txt"), issue));
        assertThat(document.getElementsByTag("code").html())
                .isEqualTo(
                        "&lt;l:main-panel&gt;Before&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;\n"
                                + "&lt;l:main-panel&gt;Warning&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;\n"
                                + "&lt;l:main-panel&gt;After&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;");
    }

    @Test
    void shouldFilterTagsInMessageAndDescription() {
        MarkerBuilder builder = new MarkerBuilder();

        Marker issue = builder.withLineStart(7)
                .withTitle("Hello <b>Message</b> <script>execute</script>")
                .withDescription("Hello <b>Description</b> <script>execute</script>")
                .build();

        SourcePrinter printer = new SourcePrinter(createJenkinsFacade());

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("analysis-warning-title").html())
                .isEqualToIgnoringWhitespace("Hello <b>Message</b>");
        assertThat(document.getElementsByClass("analysis-detail").html())
                .isEqualToIgnoringWhitespace("Hello <b>Description</b>");
    }

    @Test
    void shouldMarkTheCodeBetweenColumnStartAndColumnEnd() {
        MarkerBuilder builder = new MarkerBuilder();

        Marker issue = builder.withLineStart(5)
                .withColumnStart(11)
                .withColumnEnd(25)
                .withDescription("Hello <b>Description</b> <script>execute</script>")
                .build();

        SourcePrinter printer = new SourcePrinter(createJenkinsFacade());
        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-cpp.txt"), issue));

        assertThat(document.getElementsByTag("code").html()).isEqualTo(
                "#include &lt;iostream&gt;\n"
                        + "\n"
                        + "int main(int argc, char**argv) {\n"
                        + "int b = <span class=\"code-mark\">std::move(argc)</span>;\n"
                        + "std::cout &lt;&lt; \"Hello, World!\" &lt;&lt; argc &lt;&lt; std::endl;\n"
                        + "  return 0;\n"
                        + "}");
    }

    @Test
    void shouldNotMarkTheCodeIfStartLineAndEndLineAreDifferent() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(5)
                .withColumnStart(11)
                .withColumnEnd(25)
                .withLineStart(2)
                .withLineEnd(5)
                .withDescription("Hello <b>Description</b> <script>execute</script>")
                .build();

        SourcePrinter printer = new SourcePrinter(createJenkinsFacade());
        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-cpp.txt"), issue));

        assertThat(document.getElementsByTag("code").toString()).isEqualTo(
                "<code class=\"language-clike line-numbers match-braces\">#include &lt;iostream&gt;\n"
                        + "</code>\n"
                        + "<code class=\"language-clike line-numbers highlight match-braces\">\n"
                        + "int main(int argc, char**argv) {\n"
                        + "\n"
                        + "  int b = std::move(argc);\n"
                        + "</code>\n"
                        + "<code class=\"language-clike line-numbers match-braces\">\n"
                        + "  std::cout &lt;&lt; \"Hello, World!\" &lt;&lt; argc &lt;&lt; std::endl;\n"
                        + "  return 0;\n"
                        + "}\n"
                        + "</code>"
        );
    }

    @Test
    void shouldAddBreakOnNewLine() {
        MarkerBuilder builder = new MarkerBuilder();

        Marker issue = builder.withLineStart(7).withTitle("Hello <b>MessageLine1\nLine2\nLine3</b>").build();

        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("analysis-warning-title").html())
                .isEqualToIgnoringWhitespace("Hello <b>MessageLine1<br>Line2<br>Line3</b>");
        assertThat(document.getElementsByClass("analysis-detail")).isEmpty();
        assertThat(document.getElementsByClass("collapse-panel")).isEmpty();
    }

    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-55679")
    void shouldRenderXmlFiles() {
        MarkerBuilder builder = new MarkerBuilder();
        SourcePrinter printer = new SourcePrinter();

        Marker issue = builder.build();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format.xml"), issue));
        String expectedFile = toString("format.xml");

        assertThat(document.text()).isEqualToIgnoringWhitespace(expectedFile);

        Elements pre = document.getElementsByTag("pre");
        assertThat(pre.text()).isEqualToIgnoringWhitespace(expectedFile);
    }

    private JenkinsFacade createJenkinsFacade() {
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);
        when(jenkinsFacade.getImagePath(anyString())).thenReturn("/path/to/icon");
        return jenkinsFacade;
    }

    @Nested
    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC")
    class ColumnMarkerTest {
        @Test
        void withColumnStartZeroThenDontMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 0, 0))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnStartAndColumnEndZeroThenMarkFromStartToLineEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 6, 0))
                    .contains("text OpEnMARKthat could be codeClOsEMARK");
        }

        @Test
        void givenColumnStartAndColumnEndwithColumnEndPointingToLineEndThenMarkFromStartToLineEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 6, 23))
                    .contains("text OpEnMARKthat could be codeClOsEMARK");
        }

        @Test
        void givenColumnStartAndColumnEndThenMarkFromColumnStartToColumnEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 6, 10))
                    .contains("text OpEnMARKthat ClOsEMARKcould be code");
        }

        @Test
        void givenColumnStartAndColumnEndWithDifferenceOfOneThenMarkFromColumnStartToColumnEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 6, 7))
                    .contains("text OpEnMARKthClOsEMARKat could be code");
        }

        @Test
        void givenColumnStartAndColumnEndWithSameValueThenMarkOneCharacter() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 6, 6))
                    .contains("text OpEnMARKtClOsEMARKhat could be code");
        }

        @Test
        void givenAnEmptyTextThenMarkNothing() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("", 6, 6))
                    .contains("");
        }

        @Test
        void givenColumnStartWithValueOneThenMarkTheLineFromBegin() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 1, 6))
                    .contains("OpEnMARKtext tClOsEMARKhat could be code");
        }

        @Test
        void givenColumnStartWithValueOfTheLastCharacterThenMarkTheLastCharacter() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 23, 0))
                    .contains("text that could be codOpEnMARKeClOsEMARK");
        }

        @Test
        void givenColumnStartWithValueOfBehindColumnEndThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 23, 10))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnStartIsAfterLineEndThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 30, 10))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnStartIsNegativeThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", -1, 10))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnEndIsNegativeThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 1, -1))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnEndIsAfterLineEndThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK")
                    .markColumns("text that could be code", 1, 24))
                    .contains("text that could be code");
        }
    }
}
