package io.jenkins.plugins.prism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import edu.hm.hafner.util.ResourceTest;
import io.jenkins.plugins.prism.Marker.MarkerBuilder;
import io.jenkins.plugins.prism.Marker.Severity;
import io.jenkins.plugins.util.JenkinsFacade;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link SourcePrinter}.
 *
 * @author Ullrich Hafner
 */
class SourcePrinterTest extends ResourceTest {
    private static final String MESSAGE = "Hello Message";
    private static final String DESCRIPTION = "Hello Description";
    private static final String FILE_NAME = "filename.unknown";
    private static final String ICON = "/plugin/xyz/icon";
    private static final String CPP_FILE = "format-cpp.txt";

    /**
     * Verifies that the source code is rendered as a single hidden code block, so it can be colorized at once on the
     * client side.
     */
    @Test
    void shouldRenderSourceCodeAsSingleBlock() {
        var document = render(FILE_NAME, asStream("format-java.txt"), List.of());

        var code = getCode(document);
        assertThat(code.wholeText()).isEqualTo(joinLines("format-java.txt"));
        assertThat(code.attr("data-lines"))
                .isEqualTo(String.valueOf(asStream("format-java.txt").count()));
        assertThat(code.classNames()).containsExactly("language-clike");
        assertThat(document.getElementsByClass("prism-source__raw").attr("hidden"))
                .isEqualTo("hidden");
        assertThat(document.getElementsByClass("prism-source__markers").attr("hidden"))
                .isEqualTo("hidden");
        assertThat(document.getElementsByClass("prism-marker")).isEmpty();
    }

    /**
     * Verifies that HTML tags in the source code are escaped.
     */
    @Test
    void shouldEscapeTagsInCode() {
        var marker = new MarkerBuilder()
                .withLineStart(2)
                .withColumnStart(1)
                .withColumnEnd(14)
                .build();

        var document = render(FILE_NAME, asStream("format-jelly.txt"), List.of(marker));

        assertThat(getCode(document).html())
                .isEqualTo("&lt;l:main-panel&gt;Before&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;\n"
                        + "<span class=\"code-mark\" data-markers=\"prism-marker-1\">&lt;l:main-panel&gt;</span>"
                        + "Warning&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;\n"
                        + "&lt;l:main-panel&gt;After&lt;script&gt;execute&lt;/script&gt; Text&lt;/l:main-panel&gt;");
        assertThat(document.getElementsByTag("script")).isEmpty();
    }

    /**
     * Verifies that the title, the description, and the icon of a marker are shown.
     */
    @Test
    void shouldShowTitleDescriptionAndIcon() {
        var marker = new MarkerBuilder()
                .withLineStart(7)
                .withTitle(MESSAGE)
                .withDescription(DESCRIPTION)
                .withIcon(ICON)
                .build();
        var jenkins = mock(JenkinsFacade.class);
        when(jenkins.getImagePath(ICON)).thenReturn("/resolved");

        var document =
                Jsoup.parse(new SourcePrinter(jenkins).render(FILE_NAME, asStream("format-java.txt"), List.of(marker)));

        assertThat(document.getElementsByClass("prism-marker__title").text()).isEqualTo(MESSAGE);
        assertThat(document.getElementsByClass("prism-marker__description").text())
                .isEqualTo(DESCRIPTION);
        assertThat(document.getElementsByClass("prism-marker__details")).hasSize(1);
        assertThat(document.getElementsByClass("prism-marker__icon").attr("src"))
                .isEqualTo("/resolved");
    }

    /**
     * Verifies that a marker without description has no collapsible description.
     */
    @Test
    void shouldCreateMarkerWithoutDescription() {
        var marker = new MarkerBuilder().withLineStart(7).withTitle(MESSAGE).build();

        var document = render(FILE_NAME, asStream("format-java.txt"), List.of(marker));

        assertThat(document.getElementsByClass("prism-marker__title").text()).isEqualTo(MESSAGE);
        assertThat(document.getElementsByClass("prism-marker__description")).isEmpty();
        assertThat(document.getElementsByClass("prism-marker__details")).isEmpty();
    }

    /**
     * Verifies that HTML tags in the title and the description are sanitized.
     */
    @Test
    void shouldFilterTagsInMessageAndDescription() {
        var marker = new MarkerBuilder()
                .withLineStart(7)
                .withTitle("Hello <b>Message</b> <script>execute</script>")
                .withDescription("Hello <b>Description</b> <script>execute</script>")
                .build();

        var document = render(FILE_NAME, asStream("format-java.txt"), List.of(marker));

        assertThat(document.getElementsByClass("prism-marker__title").html())
                .isEqualToIgnoringWhitespace("Hello <b>Message</b>");
        assertThat(document.getElementsByClass("prism-marker__description").html())
                .isEqualToIgnoringWhitespace("Hello <b>Description</b>");
        assertThat(document.getElementsByTag("script")).isEmpty();
    }

    /**
     * Verifies that line breaks in the title are shown.
     */
    @Test
    void shouldAddBreakOnNewLine() {
        var marker = new MarkerBuilder()
                .withLineStart(7)
                .withTitle("Hello <b>MessageLine1\nLine2\nLine3</b>")
                .build();

        var document = render(FILE_NAME, asStream("format-java.txt"), List.of(marker));

        assertThat(document.getElementsByClass("prism-marker__title").html())
                .isEqualToIgnoringWhitespace("Hello <b>MessageLine1<br>Line2<br>Line3</b>");
    }

    /**
     * Verifies that all markers are rendered with their position, sorted by their anchor line. Markers without an
     * ID get a generated ID.
     */
    @Test
    void shouldRenderAllMarkersWithTheirPosition() {
        var first = new MarkerBuilder().withLineStart(3).withTitle("First").build();
        var second = new MarkerBuilder()
                .withLineStart(7)
                .withLineEnd(8)
                .withTitle("Second")
                .withId("second")
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(second, first));

        assertThat(document.getElementsByClass("prism-marker"))
                .extracting(
                        Element::id,
                        e -> e.attr("data-line-start"),
                        e -> e.attr("data-line-end"),
                        e -> e.attr("data-anchor-line"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("prism-marker-2", "3", "3", "3"),
                        org.assertj.core.groups.Tuple.tuple("second", "7", "8", "8"));
        assertThat(document.getElementsByClass("prism-marker__title"))
                .extracting(Element::text)
                .containsExactly("First", "Second");
    }

    /**
     * Verifies that overlapping markers and several markers in the same line are sorted by their anchor line and
     * their start line.
     */
    @Test
    void shouldSortOverlappingMarkers() {
        var block = new MarkerBuilder()
                .withLineStart(3)
                .withLineEnd(5)
                .withTitle("Block")
                .build();
        var inner = new MarkerBuilder().withLineStart(4).withTitle("Inner").build();
        var sameLine =
                new MarkerBuilder().withLineStart(4).withTitle("Same line").build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(block, inner, sameLine));

        assertThat(document.getElementsByClass("prism-marker__title"))
                .extracting(Element::text)
                .containsExactly("Inner", "Same line", "Block");
    }

    /**
     * Verifies that markers without a line are shown before the first line and markers after the end of the file
     * are shown after the last line. Markers without a title and description are empty: they only mark lines.
     */
    @Test
    void shouldPositionMarkersOutsideOfFile() {
        var noLine = new MarkerBuilder().withTitle("No line").build();
        var afterEnd =
                new MarkerBuilder().withLineStart(100).withTitle("After end").build();
        var noText = new MarkerBuilder().withLineStart(2).build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(afterEnd, noText, noLine));

        assertThat(document.getElementsByClass("prism-marker"))
                .extracting(Element::id, e -> e.attr("data-anchor-line"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("prism-marker-3", "0"),
                        org.assertj.core.groups.Tuple.tuple("prism-marker-2", "2"),
                        org.assertj.core.groups.Tuple.tuple("prism-marker-1", "9"));
        var empty = document.getElementById("prism-marker-2");
        assertThat(empty).isNotNull();
        assertThat(empty.classNames()).contains("prism-marker--empty");
        assertThat(empty.childrenSize()).isZero();
    }

    /**
     * Verifies that the columns of a marker are marked.
     */
    @Test
    void shouldMarkTheCodeBetweenColumnStartAndColumnEnd() {
        var marker = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(11)
                .withColumnEnd(25)
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(marker));

        assertThat(document.getElementsByClass("code-mark"))
                .extracting(Element::text, e -> e.attr("data-markers"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("std::move(argc)", "prism-marker-1"));
        assertThat(getCode(document).wholeText()).isEqualTo(joinLines(CPP_FILE));
    }

    /**
     * Verifies that several column ranges in the same line are marked: overlapping ranges are merged and list the
     * IDs of all their markers.
     */
    @Test
    void shouldMarkSeveralColumnRangesInTheSameLine() {
        var type = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(3)
                .withColumnEnd(5)
                .withId("type")
                .build();
        var call = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(11)
                .withColumnEnd(19)
                .withId("call")
                .build();
        var overlapping = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(15)
                .withColumnEnd(25)
                .withId("args")
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(overlapping, type, call));

        assertThat(document.getElementsByClass("code-mark"))
                .extracting(Element::text, e -> e.attr("data-markers"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("int", "type"),
                        org.assertj.core.groups.Tuple.tuple("std::move(argc)", "args call"));
    }

    /**
     * Verifies that columns outside the line are handled: the end column is limited to the end of the line and
     * columns after the end of the line are not marked.
     */
    @Test
    void shouldHandleColumnsOutsideOfLine() {
        var tooLong = new MarkerBuilder()
                .withLineStart(5)
                .withColumnStart(11)
                .withColumnEnd(100)
                .build();
        var afterEnd = new MarkerBuilder()
                .withLineStart(3)
                .withColumnStart(100)
                .withColumnEnd(110)
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(tooLong, afterEnd));

        assertThat(document.getElementsByClass("code-mark"))
                .extracting(Element::text)
                .containsExactly("std::move(argc);");
        assertThat(getCode(document).wholeText()).isEqualTo(joinLines(CPP_FILE));
    }

    /**
     * Verifies that columns of markers that span several lines are not marked.
     */
    @Test
    void shouldNotMarkColumnsIfStartLineAndEndLineAreDifferent() {
        var marker = new MarkerBuilder()
                .withLineStart(2)
                .withLineEnd(5)
                .withColumnStart(11)
                .withColumnEnd(25)
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(marker));

        assertThat(document.getElementsByClass("code-mark")).isEmpty();
    }

    /**
     * Verifies that the severity, the badges, and the links of a marker are shown. Links with JavaScript URLs are
     * skipped.
     */
    @Test
    void shouldShowSeverityBadgesAndLinks() {
        var marker = new MarkerBuilder()
                .withLineStart(3)
                .withTitle("Title")
                .withSeverity(Severity.ERROR)
                .withBadge("New")
                .withBadge("PMD <b>", "Tool")
                .withBadge("Ulli · 3eaad0e", "Commit", "https://github.com/commit/3eaad0e")
                .withBadge("Unsafe", "", "javascript:alert(1)")
                .withLink("Details", "details/1", ICON)
                .withLink("Unsafe", " JavaScript:alert(1)")
                .withLink("Commit", "https://github.com/commit/1")
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(marker));

        var box = document.getElementsByClass("prism-marker").first();
        assertThat(box).isNotNull();
        assertThat(box.classNames()).containsExactly("prism-marker", "prism-marker--error");
        assertThat(box.attr("data-severity")).isEqualTo("error");
        assertThat(document.getElementsByClass("prism-marker__badge"))
                .extracting(Element::text, e -> e.attr("tooltip"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("New", ""),
                        org.assertj.core.groups.Tuple.tuple("PMD <b>", "Tool"),
                        org.assertj.core.groups.Tuple.tuple("Ulli · 3eaad0e", "Commit"),
                        org.assertj.core.groups.Tuple.tuple("Unsafe", ""));
        assertThat(document.getElementsByClass("prism-marker__badge--link"))
                .extracting(Element::tagName, e -> e.attr("href"), e -> e.attr("target"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", "https://github.com/commit/3eaad0e", "_blank"));
        assertThat(document.getElementsByClass("prism-marker__badge").select("b"))
                .isEmpty();
        assertThat(document.getElementsByClass("prism-marker__link"))
                .extracting(Element::text, e -> e.attr("href"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Details", "details/1"),
                        org.assertj.core.groups.Tuple.tuple("Commit", "https://github.com/commit/1"));
        assertThat(document.getElementsByClass("prism-marker__link-icon").attr("src"))
                .isEqualTo("/path/to/icon");
    }

    /**
     * Verifies that a marker without badges and links has no containers for them and uses the default severity.
     */
    @Test
    void shouldSkipEmptyBadgesAndLinks() {
        var marker = new MarkerBuilder().withLineStart(3).withTitle("Title").build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(marker));

        assertThat(document.getElementsByClass("prism-marker--warning")).hasSize(1);
        assertThat(document.getElementsByClass("prism-marker__badges")).isEmpty();
        assertThat(document.getElementsByClass("prism-marker__links")).isEmpty();
    }

    /**
     * Verifies that the data of a marker is rendered as attributes of the box, so client side scripts can filter
     * the markers.
     */
    @Test
    void shouldRenderDataOfMarkers() {
        var marker = new MarkerBuilder()
                .withLineStart(3)
                .withTitle("Title")
                .withData("state", "new")
                .withData("tool-id", "pmd")
                .build();

        var document = render(FILE_NAME, asStream(CPP_FILE), List.of(marker));

        var box = document.getElementsByClass("prism-marker").first();
        assertThat(box).isNotNull();
        assertThat(box.attr("data-marker-state")).isEqualTo("new");
        assertThat(box.attr("data-marker-tool-id")).isEqualTo("pmd");
    }

    /**
     * Verifies that the name of marker data is validated.
     */
    @Test
    void shouldRejectInvalidNamesOfData() {
        var builder = new MarkerBuilder();

        assertThatIllegalArgumentException().isThrownBy(() -> builder.withData("State", "new"));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.withData("1st", "new"));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.withData("a b", "new"));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.withData("\"onclick", "new"));
    }

    /**
     * Verifies that TypeScript files are colorized as TypeScript.
     */
    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-64584")
    void shouldRenderTypeScriptFileAsTypeScript() {
        var document = render("sample.ts", Stream.of("const answer: number = 42;"), List.of());

        assertThat(getCode(document).classNames()).containsExactly("language-typescript");
    }

    /**
     * Verifies that Qt translation files (which use the extension of TypeScript files) are colorized as markup.
     */
    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-64584")
    void shouldRenderQtTranslationFileAsMarkup() {
        var document = render(
                "sample.ts",
                Stream.of(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<!DOCTYPE TS>",
                        "<TS version=\"2.1\" language=\"en_US\">",
                        "</TS>"),
                List.of());

        assertThat(getCode(document).classNames()).containsExactly("language-markup");
    }

    /**
     * Verifies that XML files are rendered.
     */
    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-55679")
    void shouldRenderXmlFiles() {
        var document = render("format.xml", asStream("format.xml"), List.of());

        assertThat(getCode(document).wholeText()).isEqualTo(joinLines("format.xml"));
        assertThat(getCode(document).classNames()).containsExactly("language-markup");
    }

    /**
     * Verifies that large files are not colorized.
     */
    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-73298")
    void shouldSkipSyntaxHighlightingForLargeFiles() {
        var document = render("sample.xml", Stream.generate(() -> "line").limit(5_001), List.of());

        assertThat(getCode(document).classNames()).isEmpty();
        assertThat(getCode(document).attr("data-lines")).isEqualTo("5001");
    }

    /**
     * Verifies that files within the limit are colorized.
     */
    @Test
    @org.junitpioneer.jupiter.Issue("JENKINS-73298")
    void shouldKeepSyntaxHighlightingForFilesWithinLimit() {
        var document = render("sample.xml", Stream.generate(() -> "line").limit(5_000), List.of());

        assertThat(getCode(document).classNames()).containsExactly("language-markup");
    }

    /**
     * Verifies that the variant with a single marker renders the same snippet as the variant with a list.
     */
    @Test
    void shouldRenderSingleMarker() {
        var marker = new MarkerBuilder().withLineStart(3).withTitle(MESSAGE).build();
        var printer = new SourcePrinter(createJenkinsFacade());

        assertThat(printer.render(FILE_NAME, asStream(CPP_FILE), marker))
                .isEqualTo(printer.render(FILE_NAME, asStream(CPP_FILE), List.of(marker)));
    }

    /**
     * Verifies that an excerpt keeps the line numbers of the whole file: markers before the excerpt are shown before
     * the first line, markers after the excerpt after the last line.
     */
    @Test
    void shouldRenderExcerpt() {
        var before = new MarkerBuilder()
                .withLineStart(5)
                .withTitle("Before")
                .withId("before")
                .build();
        var inside = new MarkerBuilder()
                .withLineStart(121)
                .withColumnStart(6)
                .withColumnEnd(8)
                .withTitle("Inside")
                .withId("inside")
                .build();
        var overlapping = new MarkerBuilder()
                .withLineStart(100)
                .withLineEnd(120)
                .withTitle("Overlapping")
                .withId("overlapping")
                .build();
        var after = new MarkerBuilder()
                .withLineStart(500)
                .withTitle("After")
                .withId("after")
                .build();

        var document = Jsoup.parse(new SourcePrinter(createJenkinsFacade())
                .render(
                        FILE_NAME,
                        Stream.of("line 120", "line 121", "line 122"),
                        List.of(after, inside, overlapping, before),
                        120,
                        false));

        var code = getCode(document);
        assertThat(code.attr("data-first-line")).isEqualTo("120");
        assertThat(code.attr("data-lines")).isEqualTo("3");
        assertThat(code.wholeText()).isEqualTo("line 120\nline 121\nline 122");
        assertThat(document.getElementsByClass("prism-marker"))
                .extracting(Element::id, e -> e.attr("data-anchor-line"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("before", "119"),
                        org.assertj.core.groups.Tuple.tuple("overlapping", "120"),
                        org.assertj.core.groups.Tuple.tuple("inside", "121"),
                        org.assertj.core.groups.Tuple.tuple("after", "122"));
        assertThat(document.getElementsByClass("code-mark"))
                .extracting(Element::text, e -> e.attr("data-markers"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("121", "inside"));
    }

    /**
     * Verifies that plain text is not colorized: either if requested explicitly or for log and text files.
     */
    @Test
    void shouldRenderPlainText() {
        var printer = new SourcePrinter(createJenkinsFacade());

        assertThat(getCode(Jsoup.parse(printer.render("Hello.java", Stream.of("class Hello {}"), List.of(), 1, true)))
                        .classNames())
                .isEmpty();
        assertThat(getCode(render("build.log", Stream.of("[INFO] Hello"), List.of()))
                        .classNames())
                .isEmpty();
        assertThat(getCode(render("notes.txt", Stream.of("Hello"), List.of())).classNames())
                .isEmpty();
        assertThat(getCode(render("Hello.java", Stream.of("class Hello {}"), List.of()))
                        .classNames())
                .containsExactly("language-java");
    }

    /**
     * Joins the lines of the specified resource with a line feed, like the source printer does. Comparing with the
     * content of the file would fail on Windows, where the files are checked out with CR LF line endings.
     *
     * @param fileName
     *         the name of the resource
     *
     * @return the lines of the resource, joined with a line feed
     */
    private String joinLines(final String fileName) {
        return asStream(fileName).collect(Collectors.joining("\n"));
    }

    private Document render(final String fileName, final Stream<String> lines, final List<Marker> markers) {
        return Jsoup.parse(new SourcePrinter(createJenkinsFacade()).render(fileName, lines, markers));
    }

    private Element getCode(final Document document) {
        return Objects.requireNonNull(
                document.select(".prism-source__raw > code").first());
    }

    private JenkinsFacade createJenkinsFacade() {
        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);
        when(jenkinsFacade.getImagePath(anyString())).thenReturn("/path/to/icon");
        return jenkinsFacade;
    }
}
