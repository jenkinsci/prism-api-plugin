package io.jenkins.plugins.prism;

import static j2html.TagCreator.a;
import static j2html.TagCreator.code;
import static j2html.TagCreator.details;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.img;
import static j2html.TagCreator.pre;
import static j2html.TagCreator.span;
import static j2html.TagCreator.summary;

import edu.hm.hafner.util.VisibleForTesting;
import io.jenkins.plugins.prism.Marker.Badge;
import io.jenkins.plugins.prism.Marker.Link;
import io.jenkins.plugins.prism.Marker.Severity;
import io.jenkins.plugins.prism.MarkerLayout.ColumnRange;
import io.jenkins.plugins.prism.MarkerLayout.PositionedMarker;
import io.jenkins.plugins.util.JenkinsFacade;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;

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
    private static final String QT_LINGUIST_PATTERN = "<!DOCTYPE TS>";
    private static final int MAX_LINES_FOR_SYNTAX_HIGHLIGHTING = 5_000;
    private static final String LINE_NUMBERS = "line-numbers";
    private static final String MATCH_BRACES = "match-braces";
    private static final String ICON_MD = "icon-md";
    private static final String ICON_SM = "icon-sm";
    private static final String MARKER_HEADER = "prism-marker__header";
    private static final String MARKER_ICON = "prism-marker__icon";
    private static final char NEW_LINE = '\n';

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
     * Creates a colorized HTML snippet with the specified source code. Highlights the specified marker and provides a
     * clickable and collapsible element that shows the details for the marker.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the source code
     * @param marker
     *         the marker to show
     *
     * @return the source code as colorized HTML
     */
    String render(final String fileName, final Stream<String> lines, final Marker marker) {
        return render(fileName, lines, List.of(marker));
    }

    /**
     * Creates a colorized HTML snippet with the specified source code. Highlights all specified markers and shows the
     * details of each marker (title and description) in a box after the last line of the marker. Markers may
     * overlap, and several markers may refer to the same line.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the source code
     * @param markers
     *         the markers to show, the list may be empty
     *
     * @return the source code as colorized HTML
     */
    String render(final String fileName, final Stream<String> lines, final List<Marker> markers) {
        List<String> source = lines.toList();

        String language = selectLanguageClass(fileName, source);
        boolean enableSyntaxHighlighting = source.size() <= MAX_LINES_FOR_SYNTAX_HIGHLIGHTING;
        var writer = new CodeWriter(
                getCodeClasses(language, enableSyntaxHighlighting),
                getMarkedCodeClasses(language, enableSyntaxHighlighting));

        var layout = new MarkerLayout(markers, source.size());
        writer.appendMarkers(layout.getMarkersAfter(0));
        for (int line = 1; line <= source.size(); line++) {
            String text = source.get(line - 1);
            if (layout.isMarked(line)) {
                writer.appendMarkedLine(
                        markColumns(text, layout.getColumnRanges(line)),
                        layout.getSeverity(line),
                        layout.getMarkerIds(line));
            } else {
                writer.appendLine(text);
            }
            writer.appendMarkers(layout.getMarkersAfter(line));
        }
        return writer.render();
    }

    private boolean hasText(final Marker marker) {
        return StringUtils.isNotBlank(marker.getTitle()) || StringUtils.isNotBlank(marker.getDescription());
    }

    private String markColumns(final String text, final List<ColumnRange> ranges) {
        var marked = new StringBuilder(text);
        // Mark the ranges from right to left, so the columns of the remaining ranges are still valid
        for (int i = ranges.size() - 1; i >= 0; i--) {
            var range = ranges.get(i);
            marked = COLUMN_MARKER.markColumns(marked.toString(), range.start(), Math.min(range.end(), text.length()));
        }
        return marked.toString();
    }

    private String[] getCodeClasses(final String language, final boolean enableSyntaxHighlighting) {
        if (enableSyntaxHighlighting) {
            return new String[] {language, LINE_NUMBERS, MATCH_BRACES};
        }
        return new String[0];
    }

    private String[] getMarkedCodeClasses(final String language, final boolean enableSyntaxHighlighting) {
        if (enableSyntaxHighlighting) {
            return new String[] {language, LINE_NUMBERS, "highlight", MATCH_BRACES};
        }
        return new String[] {"highlight"};
    }

    private String createInfoPanel(final Marker marker, final String id) {
        var panel = div().withClasses(
                        "prism-marker", "prism-marker--" + marker.getSeverity().getCssModifier())
                .withId(id)
                .attr("data-line-start", marker.getLineStart())
                .attr("data-line-end", marker.getLineEnd())
                .attr("data-severity", marker.getSeverity().getCssModifier());
        marker.getData().forEach((name, value) -> panel.attr("data-marker-" + name, value));
        if (StringUtils.isBlank(marker.getDescription())) {
            panel.with(div().withClass(MARKER_HEADER).with(createHeader(marker)));
        } else {
            panel.with(details()
                    .withClass("prism-marker__details")
                    .with(
                            summary().withClass(MARKER_HEADER).with(createHeader(marker)),
                            div().withClass("prism-marker__description").with(unescape(marker.getDescription()))));
        }
        var links = marker.getLinks().stream().filter(this::isSafe).toList();
        if (!links.isEmpty()) {
            panel.with(div().withClass("prism-marker__links").with(each(links, this::createLink)));
        }
        return panel.render();
    }

    private List<DomContent> createHeader(final Marker marker) {
        var header = new ArrayList<DomContent>();
        if (StringUtils.isNotBlank(marker.getIcon())) {
            header.add(createIcon(marker.getIcon(), ICON_MD, MARKER_ICON));
        }
        header.add(span().withClass("prism-marker__title").with(replaceNewLine(marker.getTitle())));
        if (!marker.getBadges().isEmpty()) {
            header.add(span().withClass("prism-marker__badges").with(each(marker.getBadges(), this::createBadge)));
        }
        return header;
    }

    private DomContent createBadge(final Badge badge) {
        var element = span().withClass("prism-marker__badge").withText(badge.getText());
        if (StringUtils.isNotBlank(badge.getTooltip())) {
            element.attr("tooltip", badge.getTooltip());
        }
        return element;
    }

    private boolean isSafe(final Link link) {
        return !Strings.CI.startsWith(StringUtils.strip(link.getUrl()), "javascript:");
    }

    private DomContent createLink(final Link link) {
        var element = a().withClass("prism-marker__link").withHref(link.getUrl());
        if (StringUtils.isNotBlank(link.getIcon())) {
            element.with(createIcon(link.getIcon(), ICON_SM, "prism-marker__link-icon"));
        }
        return element.with(span().withText(link.getLabel()));
    }

    private DomContent createIcon(final String name, final String... classes) {
        if (name.startsWith("symbol")) {
            String symbol = Symbol.get(new SymbolRequest.Builder()
                    .withRaw(name)
                    .withClasses(String.join(" ", classes))
                    .build());
            return new UnescapedText(symbol);
        }
        return img().withSrc(jenkinsFacade.getImagePath(name)).withClasses(classes);
    }

    private UnescapedText replaceNewLine(final String message) {
        return unescape(message.replace("\n", "<br>"));
    }

    private UnescapedText unescape(final String message) {
        return new UnescapedText(SANITIZER.render(message));
    }

    @SuppressWarnings({"javancss", "PMD.CyclomaticComplexity"})
    private String selectLanguageClass(final String fileName, final List<String> lines) {
        String extension = StringUtils.substringAfterLast(fileName, ".");

        if ("ts".equals(extension) && lines.stream().anyMatch(line -> Strings.CS.contains(line, QT_LINGUIST_PATTERN))) {
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

    private String asMarkedCode(final StringBuilder text, final List<String> markerIds, final String... classes) {
        String sanitized = SANITIZER.render(StringEscapeUtils.escapeHtml4(text.toString()));
        String markerReplaced = COLUMN_MARKER.replacePlaceHolderWithHtmlTag(sanitized);
        return code().withClasses(classes)
                .attr("data-markers", String.join(" ", markerIds))
                .with(new UnescapedText(markerReplaced))
                .render();
    }

    private String asCode(final StringBuilder text, final String... classes) {
        return code().withClasses(classes)
                .with(unescape(StringEscapeUtils.escapeHtml4(text.toString())))
                .render();
    }

    /**
     * Writes the blocks of the source code and the boxes of the markers. Consecutive lines with the same state (not
     * marked, or marked by the same markers) are combined into a single code block, since Prism colorizes each code
     * block separately. Marked code blocks list the IDs of their markers in the attribute {@code data-markers}, so
     * the highlight of a block can be removed on the client side if all of its markers are hidden by a filter.
     */
    @SuppressWarnings("PMD.AvoidStringBufferField")
    private final class CodeWriter {
        private final StringBuilder code = new StringBuilder();
        private final StringBuilder block = new StringBuilder();
        private final String[] codeClasses;
        private final String[] markedCodeClasses;
        private boolean isBlockMarked;
        private Severity blockSeverity = Severity.WARNING;
        private List<String> blockMarkerIds = List.of();

        @SuppressWarnings("PMD.UseVarargs")
        CodeWriter(final String[] codeClasses, final String[] markedCodeClasses) {
            this.codeClasses = codeClasses.clone();
            this.markedCodeClasses = markedCodeClasses.clone();
        }

        void appendLine(final String text) {
            if (isBlockMarked) {
                flush();
                isBlockMarked = false;
            }
            block.append(text).append(NEW_LINE);
        }

        void appendMarkedLine(final String text, final Severity severity, final List<String> markerIds) {
            if (!isBlockMarked || severity != blockSeverity || !markerIds.equals(blockMarkerIds)) {
                flush();
                isBlockMarked = true;
                blockSeverity = severity;
                blockMarkerIds = markerIds;
            }
            block.append(text).append(NEW_LINE);
        }

        void appendMarkers(final List<PositionedMarker> markers) {
            if (markers.isEmpty()) {
                return;
            }
            flush();
            for (PositionedMarker positioned : markers) {
                if (hasText(positioned.marker())) {
                    code.append(createInfoPanel(positioned.marker(), positioned.id()));
                }
            }
        }

        String render() {
            flush();
            return pre().with(new UnescapedText(code.toString())).renderFormatted();
        }

        private void flush() {
            if (block.isEmpty()) {
                return;
            }
            if (isBlockMarked) {
                code.append(asMarkedCode(
                        block,
                        blockMarkerIds,
                        ArrayUtils.add(markedCodeClasses, "prism-marked--" + blockSeverity.getCssModifier())));
            } else {
                code.append(asCode(block, codeClasses));
            }
            block.setLength(0);
        }
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
            return text.replaceAll(openingTagPlaceHolder, OPENING_TAG).replaceAll(closingTagPlaceHolder, CLOSING_TAG);
        }
    }
}
