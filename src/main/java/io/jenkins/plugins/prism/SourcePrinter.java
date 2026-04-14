package io.jenkins.plugins.prism;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.jenkins.ui.symbol.SymbolRequest.Builder;

import edu.hm.hafner.util.LookaheadStream;
import edu.hm.hafner.util.VisibleForTesting;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import java.util.stream.Stream;

import io.jenkins.plugins.util.JenkinsFacade;

import static j2html.TagCreator.*;

/**
 * Renders a source code file into a HTML snippet using Prism.js.
 *
 * @author Philippe Arteau
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.GodClass")
class SourcePrinter {
    private static final Sanitizer SANITIZER = new Sanitizer();

    private static final ColumnMarker COLUMN_MARKER = new ColumnMarker("-n/a-");
    private static final String LINE_NUMBERS = "line-numbers";
    private static final String MATCH_BRACES = "match-braces";
    private static final String ICON_MD = "icon-md";

    private final JenkinsFacade jenkinsFacade;

    /**
     * Creates a new instance of {@link SourcePrinter}.
     */
    SourcePrinter() {
        this(new JenkinsFacade());
    }

    @VisibleForTesting
    SourcePrinter(final JenkinsFacade jenkinsFacade) {
        this.jenkinsFacade = jenkinsFacade;
    }

    /**
     * Creates a colorized HTML snippet with the specified source code. Highlights the specified issue and provides a
     * clickable and collapsible element that shows the details for the issue.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the source code
     * @param marker
     *         the issue to show
     *
     * @return the source code as colorized HTML
     */
    String render(final String fileName, final Stream<String> lines, final Marker marker) {
        try (LookaheadStream stream = new LookaheadStream(lines)) {
            int start = marker.getLineStart();
            int end = marker.getLineEnd();

            StringBuilder before = readBlockUntilLine(stream, start - 1);
            StringBuilder marked = readBlockUntilLine(stream, end);
            StringBuilder after = readBlockUntilLine(stream, Integer.MAX_VALUE);

            String language = selectLanguageClass(fileName, before, marked, after);
            String code = asCode(before, language, LINE_NUMBERS, MATCH_BRACES)
                    + asMarkedCode(marked, marker, language, LINE_NUMBERS, "highlight", MATCH_BRACES)
                    + createInfoPanel(marker)
                    + asCode(after, language, LINE_NUMBERS, MATCH_BRACES);

            return pre().with(new UnescapedText(code)).renderFormatted();
        }
    }

    private StringBuilder readBlockUntilLine(final LookaheadStream stream, final int end) {
        StringBuilder marked = new StringBuilder();
        while (stream.hasNext() && stream.getLine() < end) {
            marked.append(stream.next());
            marked.append("\n");
        }
        return marked;
    }

    private String createInfoPanel(final Marker marker) {
        return createBox(marker).withClass("analysis-warning").render();
    }

    private ContainerTag createBox(final Marker marker) {
        if (StringUtils.isEmpty(marker.getDescription())) {
            return createTitle(marker, false);
        }
        else {
            return createTitleAndCollapsedDescription(marker, marker.getDescription());
        }
    }

    private DomContent createIcon(final String name) {
        if (name.startsWith("symbol")) {
            String symbol = Symbol.get(new SymbolRequest.Builder()
                    .withRaw(name)
                    .withClasses(ICON_MD)
                    .build());
            return new UnescapedText(symbol);
        }
        return img().withSrc(jenkinsFacade.getImagePath(name)).withClasses(ICON_MD);
    }

    private ContainerTag createTitle(final Marker marker, final boolean isCollapseVisible) {
        return div().with(table().withClass("analysis-title").with(tr().with(
                td().with(createIcon(marker.getIcon())),
                td().withClass("analysis-title-column")
                        .with(div().withClass("analysis-warning-title").with(replaceNewLine(marker.getTitle()))),
                createCollapseButton(isCollapseVisible)
        )));
    }

    private ContainerTag createCollapseButton(final boolean isCollapseVisible) {
        ContainerTag td = td();
        if (isCollapseVisible) {
            td.with(new UnescapedText(jenkinsFacade.getSymbol(new Builder()
                    .withName("chevron-down-circle-outline")
                    .withPluginName("ionicons-api")
                    .withClasses("analysis-collapse-icon")
                    .build())));
        }
        return td;
    }

    private ContainerTag createTitleAndCollapsedDescription(final Marker marker, final String description) {
        return div().with(
                div().withClass("analysis-collapse-button").with(createTitle(marker, true)),
                div().withClasses("collapse", "analysis-detail")
                        .with(unescape(description))
                        .withId("analysis-description"));
    }

    private UnescapedText replaceNewLine(final String message) {
        return unescape(message.replace("\n", "<br>"));
    }

    private UnescapedText unescape(final String message) {
        return new UnescapedText(SANITIZER.render(message));
    }

    @SuppressWarnings({"javancss", "PMD.CyclomaticComplexity"})
    private String selectLanguageClass(final String fileName,
            final StringBuilder before, final StringBuilder marked, final StringBuilder after) {
        String extension = StringUtils.substringAfterLast(fileName, ".");

        if ("ts".equals(extension) && looksLikeMarkup(before, marked, after)) {
            return "language-markup";
        }

        return switch (extension) {
            case "htm", "html", "xml", "xsd" -> "language-markup";
            case "css" -> "language-css";
            case "js" -> "language-javascript";
            case "c" -> "language-c";
            case "cs" -> "language-csharp";
            case "cpp" -> "language-cpp";
            case "Dockerfile" -> "language-docker";
            case "go" -> "language-go";
            case "groovy" -> "language-groovy";
            case "json" -> "language-json";
            case "md" -> "language-markdown";
            case "erb", "jsp", "tag" -> "language-erb";
            case "jav", "java" -> "language-java";
            case "rb" -> "language-ruby";
            case "kt" -> "language-kotlin";
            case "vb" -> "language-vbnet";
            case "pl" -> "language-perl";
            case "php" -> "language-php";
            case "py" -> "language-python";
            case "sql" -> "language-sql";
            case "scala", "sc" -> "language-scala";
            case "swift" -> "language-swift";
            case "ts" -> "language-typescript";
            case "yaml" -> "language-yaml";
            default -> "language-clike"; // Best effort for unknown extensions
        };
    }

    private boolean looksLikeMarkup(
            final StringBuilder before, final StringBuilder marked, final StringBuilder after) {
        String content = before.toString() + marked + after;
        String trimmed = StringUtils.stripStart(content, null);

        return trimmed.startsWith("<") || trimmed.startsWith("<?xml");
    }

    private String asMarkedCode(final StringBuilder text, final Marker marker, final String... classes) {
        StringBuilder marked;
        if (marker.getLineStart() == marker.getLineEnd()) {
            marked = COLUMN_MARKER.markColumns(text.toString(), marker.getColumnStart(), marker.getColumnEnd());
        }
        else {
            marked = text;
        }

        String sanitized = SANITIZER.render(StringEscapeUtils.escapeHtml4(marked.toString()));
        String markerReplaced = COLUMN_MARKER.replacePlaceHolderWithHtmlTag(sanitized);
        return code().withClasses(classes).with(new UnescapedText(markerReplaced)).render();
    }

    private String asCode(final StringBuilder text, final String... classes) {
        return code().withClasses(classes).with(unescape(StringEscapeUtils.escapeHtml4(text.toString()))).render();
    }

    /**
     * Encloses columns between {@code start} and {@code end} with an HTML tag (see {@code openingTag} and
     * {@code closingTag}).
     */
    static final class ColumnMarker {
        private static final String OPENING_TAG = "<span class='code-mark'>";
        private static final String CLOSING_TAG = "</span>";

        /**
         * Creates a {@link ColumnMarker} that will use {@code placeHolderText} for enclosing.
         *
         * @param placeHolderText
         *         Used to construct an opening and closing text that can later be replaced with the HTML tag
         *         {@code openingTag} {@code closingTag}. It should be a text that is unlikely to appear in any source
         *         code.
         */
        ColumnMarker(final String placeHolderText) {
            openingTagPlaceHolder = "OpEn" + placeHolderText;
            closingTagPlaceHolder = "ClOsE" + placeHolderText;
        }

        private final String openingTagPlaceHolder;
        private final String closingTagPlaceHolder;

        /**
         * Encloses columns between start and end with the HTML tag {@code openingTag} {@code closingTag}. This will
         * make prism highlight the enclosed part of the line.
         *
         * @param text
         *         the source code line
         * @param start
         *         the first column in text, that needs to be marked
         * @param end
         *         the last column in text, that needs to be marked
         *
         * @return StringBuilder containing the text with the added HTML tag "mark"
         */
        StringBuilder markColumns(final String text, final int start, final int end) {
            if (start < 1 || text.isEmpty() || end > text.length()) {
                return new StringBuilder(text);
            }
            final int realStart = start - 1;
            final int realEnd = (end == 0) ? text.length() - 1 : end - 1;

            if (realStart > realEnd) {
                return new StringBuilder(text);
            }
            final int afterMark = realEnd + 1;

            final String before = text.substring(0, realStart);
            final String toBeMarked = text.substring(realStart, afterMark);
            final String after = text.substring(afterMark);

            return new StringBuilder(before)
                    .append(openingTagPlaceHolder)
                    .append(toBeMarked)
                    .append(closingTagPlaceHolder)
                    .append(after);
        }

        /**
         * Encloses columns between start and end with the HTML tag {@code openingTag} {@code closingTag}. This will
         * make prism highlight the enclosed part of the line.
         *
         * @param text
         *         the source code line
         *
         * @return String containing the text with the added html tag
         */
        String replacePlaceHolderWithHtmlTag(final String text) {
            return text.replaceAll(openingTagPlaceHolder, OPENING_TAG)
                    .replaceAll(closingTagPlaceHolder, CLOSING_TAG);
        }
    }
}
