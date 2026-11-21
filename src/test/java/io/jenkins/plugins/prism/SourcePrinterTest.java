package io.jenkins.plugins.prism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import edu.hm.hafner.util.ResourceTest;
import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import io.jenkins.plugins.prism.Marker.Severity;
import io.jenkins.plugins.util.JenkinsFacade;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        Marker issue = builder.withLineStart(7)
                .withTitle(MESSAGE)
                .withDescription(DESCRIPTION)
                .build();

        SourcePrinter printer = new SourcePrinter(createJenkinsFacade());

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("prism-marker__title").text()).isEqualTo(MESSAGE);
        assertThat(document.getElementsByClass("prism-marker__description").text())
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

        assertThat(document.getElementsByClass("prism-marker__title").text()).isEqualTo(MESSAGE);
        assertThat(document.getElementsByClass("prism-marker__description")).isEmpty();
        assertThat(document.getElementsByClass("prism-marker__details")).isEmpty();
    }

    @Test
    void shouldCreateIcon() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(7)
                .withTitle("Hello Message")
                .withIcon(ICON)
                .build();

        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        when(jenkins.getImagePath(ICON)).thenReturn("/resolved");
        SourcePrinter printer = new SourcePrinter(jenkins);

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("prism-marker__icon").attr("src"))
                .isEqualTo("/resolved");
    }

    @Test
    void shouldFilterTagsInCode() {
        MarkerBuilder builder = new MarkerBuilder();
        Marker issue = builder.withLineStart(2).build();

        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-jelly.txt"), issue));
        assertThat(document.getElementsByTag("code").html())
                .isEqualTo("&lt;l:main-panel&gt;Before&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;\n"
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

        assertThat(document.getElementsByClass("prism-marker__title").html())
                .isEqualToIgnoringWhitespace("Hello <b>Message</b>");
        assertThat(document.getElementsByClass("prism-marker__description").html())
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

        assertThat(document.getElementsByTag("code").html())
                .isEqualTo("#include &lt;iostream&gt;\n"
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

        assertThat(document.getElementsByTag("code").toString())
                .isEqualTo("<code class=\"language-clike line-numbers match-braces\">#include &lt;iostream&gt;\n"
                        + "</code>\n"
                        + "<code class=\"language-clike line-numbers highlight match-braces prism-marked--warning\">\n"
                        + "int main(int argc, char**argv) {\n"
                        + "\n"
                        + "  int b = std::move(argc);\n"
                        + "</code>\n"
                        + "<code class=\"language-clike line-numbers match-braces\">\n"
                        + "  std::cout &lt;&lt; \"Hello, World!\" &lt;&lt; argc &lt;&lt; std::endl;\n"
                        + "  return 0;\n"
                        + "}\n"
                        + "</code>");
    }

    @Test
    void shouldAddBreakOnNewLine() {
        MarkerBuilder builder = new MarkerBuilder();

        Marker issue = builder.withLineStart(7)
                .withTitle("Hello <b>MessageLine1\nLine2\nLine3</b>")
                .build();

        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render(FILE_NAME, asStream("format-java.txt"), issue));

        assertThatCodeIsEqualToSourceText(document);

        assertThat(document.getElementsByClass("prism-marker__title").html())
                .isEqualToIgnoringWhitespace("Hello <b>MessageLine1<br>Line2<br>Line3</b>");
        assertThat(document.getElementsByClass("prism-marker__description")).isEmpty();
        assertThat(document.getElementsByClass("prism-marker__details")).isEmpty();
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

    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-64584")
    void shouldRenderTypeScriptFileAsTypeScript() {
        Marker issue = new MarkerBuilder().build();
        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render("sample.ts", Stream.of("const answer: number = 42;"), issue));

        assertThat(document.getElementsByTag("code").first()).isNotNull();
        assertThat(document.getElementsByTag("code").first().classNames()).contains("language-typescript");
    }

    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-64584")
    void shouldRenderQtTranslationFileAsMarkup() {
        Marker issue = new MarkerBuilder().withLineStart(7).build();
        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(printer.render(
                "sample.ts",
                Stream.of(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<!DOCTYPE TS>",
                        "<TS version=\"2.1\" language=\"en_US\">",
                        "  <context>",
                        "    <name>MainWindow</name>",
                        "    <message>",
                        "      <source>Hello, world!</source>",
                        "      <translation>Hello, world!</translation>",
                        "    </message>",
                        "    <message>",
                        "      <source>File not found</source>",
                        "      <translation>File not found</translation>",
                        "    </message>",
                        "  </context>",
                        "</TS>"),
                issue));

        assertThat(document.getElementsByTag("code").first()).isNotNull();
        assertThat(document.getElementsByTag("code").first().classNames()).contains("language-markup");
    }

    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-73298")
    void shouldSkipSyntaxHighlightingForLargeFiles() {
        Marker issue = new MarkerBuilder().withLineStart(2_500).build();
        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(
                printer.render("sample.xml", Stream.generate(() -> "line").limit(5_001), issue));

        assertThat(document.getElementsByTag("code").first()).isNotNull();
        assertThat(document.getElementsByTag("code").first().classNames())
                .doesNotContain("language-markup", "line-numbers", "match-braces");
        assertThat(document.getElementsByTag("code").get(1).classNames())
                .containsExactly("highlight", "prism-marked--warning");
    }

    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-73298")
    void shouldKeepSyntaxHighlightingForFilesWithinLimit() {
        Marker issue = new MarkerBuilder().withLineStart(2_500).build();
        SourcePrinter printer = new SourcePrinter();

        Document document = Jsoup.parse(
                printer.render("sample.xml", Stream.generate(() -> "line").limit(5_000), issue));

        assertThat(document.getElementsByTag("code").first()).isNotNull();
        assertThat(document.getElementsByTag("code").first().classNames())
                .contains("language-markup", "line-numbers", "match-braces");
    }

    /**
     * Verifies that all markers are shown: each marked block of lines is followed by the box with the details of the
     * marker. Markers without an ID get a generated ID.
     */
    @Test
    void shouldShowAllMarkers() {
        var first = new MarkerBuilder()
                .withLineStart(3)
                .withTitle("First")
                .withDescription(DESCRIPTION)
                .build();
        var second = new MarkerBuilder()
                .withLineStart(7)
                .withLineEnd(8)
                .withTitle("Second")
                .withId("second")
                .build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(second, first)));

        assertThat(document.getElementsByTag("code").text()).isEqualToIgnoringWhitespace(toString("format-cpp.txt"));
        assertThat(document.getElementsByClass("prism-marker"))
                .extracting(Element::id)
                .containsExactly("prism-marker-2", "second");
        assertThat(document.getElementsByClass("prism-marker__title"))
                .extracting(Element::text)
                .containsExactly("First", "Second");
        assertThat(document.getElementsByClass("prism-marker__details")).hasSize(1);
        assertThat(Objects.requireNonNull(document.getElementById("second")).attr("data-line-start"))
                .isEqualTo("7");
        assertThat(Objects.requireNonNull(document.getElementById("second")).attr("data-line-end"))
                .isEqualTo("8");

        Elements highlighted = document.select("code.highlight");
        assertThat(highlighted).hasSize(2);
        assertThat(highlighted.get(0).text()).isEqualToIgnoringWhitespace("int main(int argc, char**argv) {");
        assertThat(highlighted.get(1).text())
                .isEqualToIgnoringWhitespace("std::cout << \"Hello, World!\" << argc << std::endl; return 0;");
        assertThatMarkerFollowsCode(document, "prism-marker-2", "int main(int argc, char**argv) {");
        assertThatMarkerFollowsCode(document, "second", "return 0;");
    }

    private void assertThatMarkerFollowsCode(final Document document, final String id, final String code) {
        var marker = document.getElementById(id);
        assertThat(marker).isNotNull();
        assertThat(marker.previousElementSibling()).isNotNull();
        assertThat(Objects.requireNonNull(marker.previousElementSibling()).text())
                .endsWith(code);
    }

    /**
     * Verifies that overlapping markers and several markers in the same line are shown: the union of the lines is
     * highlighted, and the boxes are shown after the last line of each marker.
     */
    @Test
    void shouldShowOverlappingMarkers() {
        var block = new MarkerBuilder()
                .withLineStart(3)
                .withLineEnd(5)
                .withTitle("Block")
                .build();
        var inner = new MarkerBuilder().withLineStart(4).withTitle("Inner").build();
        var sameLine =
                new MarkerBuilder().withLineStart(4).withTitle("Same line").build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(block, inner, sameLine)));

        assertThat(document.getElementsByTag("code").text()).isEqualToIgnoringWhitespace(toString("format-cpp.txt"));
        assertThat(document.getElementsByClass("prism-marker__title"))
                .extracting(Element::text)
                .containsExactly("Inner", "Same line", "Block");
        assertThat(document.select("code.highlight").text())
                .isEqualToIgnoringWhitespace("int main(int argc, char**argv) { int b = std::move(argc);");
        assertThat(document.select("code:not(.highlight)").text())
                .isEqualToIgnoringWhitespace(
                        "#include <iostream> std::cout << \"Hello, World!\" << argc << std::endl; return 0; }");
    }

    /**
     * Verifies that several column ranges in the same line are marked, overlapping ranges are merged.
     */
    @Test
    void shouldMarkSeveralColumnRangesInTheSameLine() {
        var type = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(3)
                .withColumnEnd(5)
                .build();
        var call = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(11)
                .withColumnEnd(19)
                .build();
        var overlapping = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(15)
                .withColumnEnd(25)
                .build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(overlapping, type, call)));

        assertThat(document.getElementsByClass("code-mark"))
                .extracting(Element::text)
                .containsExactly("int", "std::move(argc)");
    }

    /**
     * Verifies that markers without a line are shown before the first line and markers after the end of the file are
     * shown after the last line. Markers without a title and description are highlighted only.
     */
    @Test
    void shouldShowMarkersOutsideOfFile() {
        var noLine = new MarkerBuilder().withTitle("No line").build();
        var afterEnd =
                new MarkerBuilder().withLineStart(100).withTitle("After end").build();
        var noText = new MarkerBuilder().withLineStart(2).build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(afterEnd, noText, noLine)));

        assertThat(document.getElementsByClass("prism-marker"))
                .extracting(Element::id)
                .containsExactly("prism-marker-3", "prism-marker-1");
        var pre = document.getElementsByTag("pre").first();
        assertThat(pre).isNotNull();
        assertThat(pre.child(0).id()).isEqualTo("prism-marker-3");
        assertThat(pre.child(pre.childrenSize() - 1).id()).isEqualTo("prism-marker-1");
        assertThat(document.select("code.highlight")).hasSize(1);
    }

    /**
     * Verifies that a source code file without markers is rendered as a single block.
     */
    @Test
    void shouldRenderSourceWithoutMarkers() {
        Document document = Jsoup.parse(
                new SourcePrinter(createJenkinsFacade()).render(FILE_NAME, asStream("format-cpp.txt"), List.of()));

        assertThat(document.getElementsByTag("code")).hasSize(1);
        assertThat(document.getElementsByTag("code").text()).isEqualToIgnoringWhitespace(toString("format-cpp.txt"));
        assertThat(document.getElementsByClass("prism-marker")).isEmpty();
    }

    /**
     * Verifies that the severity of a marker is used for the box and for the marked lines. Lines that are covered by
     * markers with different severities are shown with the highest severity, and blocks of lines with different
     * severities are rendered as separate code blocks.
     */
    @Test
    void shouldUseSeverityOfMarkers() {
        var error = new MarkerBuilder()
                .withLineStart(3)
                .withTitle("Error")
                .withSeverity(Severity.ERROR)
                .build();
        var info = new MarkerBuilder()
                .withLineStart(3)
                .withLineEnd(5)
                .withTitle("Info")
                .withSeverity(Severity.INFO)
                .build();
        var warning = new MarkerBuilder().withLineStart(8).withTitle("Warning").build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(error, info, warning)));

        assertThat(document.getElementsByClass("prism-marker"))
                .extracting(element -> element.classNames().toString())
                .containsExactly(
                        "[prism-marker, prism-marker--error]",
                        "[prism-marker, prism-marker--info]",
                        "[prism-marker, prism-marker--warning]");
        assertThat(document.select("code.prism-marked--error").text())
                .isEqualToIgnoringWhitespace("int main(int argc, char**argv) {");
        assertThat(document.select("code.prism-marked--info").text())
                .isEqualToIgnoringWhitespace("int b = std::move(argc);");
        assertThat(document.select("code.prism-marked--warning").text()).isEqualToIgnoringWhitespace("return 0;");
    }

    /**
     * Verifies that the badges and links of a marker are shown. Links with JavaScript URLs are skipped.
     */
    @Test
    void shouldShowBadgesAndLinks() {
        var marker = new MarkerBuilder()
                .withLineStart(3)
                .withTitle("Title")
                .withBadge("New")
                .withBadge("PMD <b>", "Tool")
                .withLink("Details", "details/1", ICON)
                .withLink("Unsafe", " JavaScript:alert(1)")
                .withLink("Commit", "https://github.com/commit/1")
                .build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(marker)));

        assertThat(document.getElementsByClass("prism-marker__badge"))
                .extracting(Element::text)
                .containsExactly("New", "PMD <b>");
        assertThat(document.getElementsByClass("prism-marker__badge"))
                .extracting(element -> element.attr("tooltip"))
                .containsExactly("", "Tool");
        assertThat(document.getElementsByClass("prism-marker__badge").select("b"))
                .isEmpty();
        assertThat(document.getElementsByClass("prism-marker__link"))
                .extracting(Element::text)
                .containsExactly("Details", "Commit");
        assertThat(document.getElementsByClass("prism-marker__link"))
                .extracting(element -> element.attr("href"))
                .containsExactly("details/1", "https://github.com/commit/1");
        assertThat(document.getElementsByClass("prism-marker__link-icon").attr("src"))
                .isEqualTo("/path/to/icon");
    }

    /**
     * Verifies that a marker without badges and links has no containers for them.
     */
    @Test
    void shouldSkipEmptyBadgesAndLinks() {
        var marker = new MarkerBuilder().withLineStart(3).withTitle("Title").build();

        Document document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(FILE_NAME, asStream("format-cpp.txt"), List.of(marker)));

        assertThat(document.getElementsByClass("prism-marker--warning")).hasSize(1);
        assertThat(document.getElementsByClass("prism-marker__badges")).isEmpty();
        assertThat(document.getElementsByClass("prism-marker__links")).isEmpty();
    }

    private JenkinsFacade createJenkinsFacade() {
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);
        when(jenkinsFacade.getImagePath(anyString())).thenReturn("/path/to/icon");
        return jenkinsFacade;
    }

    @Nested
    class ColumnMarkerTest {
        @Test
        void withColumnStartZeroThenDontMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 0, 0))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnStartAndColumnEndZeroThenMarkFromStartToLineEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 6, 0))
                    .contains("text OpEnMARKthat could be codeClOsEMARK");
        }

        @Test
        void givenColumnStartAndColumnEndwithColumnEndPointingToLineEndThenMarkFromStartToLineEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 6, 23))
                    .contains("text OpEnMARKthat could be codeClOsEMARK");
        }

        @Test
        void givenColumnStartAndColumnEndThenMarkFromColumnStartToColumnEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 6, 10))
                    .contains("text OpEnMARKthat ClOsEMARKcould be code");
        }

        @Test
        void givenColumnStartAndColumnEndWithDifferenceOfOneThenMarkFromColumnStartToColumnEnd() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 6, 7))
                    .contains("text OpEnMARKthClOsEMARKat could be code");
        }

        @Test
        void givenColumnStartAndColumnEndWithSameValueThenMarkOneCharacter() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 6, 6))
                    .contains("text OpEnMARKtClOsEMARKhat could be code");
        }

        @Test
        void givenAnEmptyTextThenMarkNothing() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("", 6, 6))
                    .contains("");
        }

        @Test
        void givenColumnStartWithValueOneThenMarkTheLineFromBegin() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 1, 6))
                    .contains("OpEnMARKtext tClOsEMARKhat could be code");
        }

        @Test
        void givenColumnStartWithValueOfTheLastCharacterThenMarkTheLastCharacter() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 23, 0))
                    .contains("text that could be codOpEnMARKeClOsEMARK");
        }

        @Test
        void givenColumnStartWithValueOfBehindColumnEndThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 23, 10))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnStartIsAfterLineEndThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 30, 10))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnStartIsNegativeThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", -1, 10))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnEndIsNegativeThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 1, -1))
                    .contains("text that could be code");
        }

        @Test
        void givenColumnEndIsAfterLineEndThenDoNotMark() {
            assertThat(new SourcePrinter.ColumnMarker("MARK").markColumns("text that could be code", 1, 24))
                    .contains("text that could be code");
        }
    }
}
