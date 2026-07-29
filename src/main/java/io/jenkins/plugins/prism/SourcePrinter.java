package io.jenkins.plugins.prism;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.jenkins.ui.symbol.SymbolRequest.Builder;
import org.apache.commons.lang3.Strings;

import edu.hm.hafner.util.LookaheadStream;
import edu.hm.hafner.util.VisibleForTesting;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final String QT_LINGUIST_PATTERN = "<!DOCTYPE TS>";
    private static final int MAX_LINES_FOR_SYNTAX_HIGHLIGHTING = 5_000;
    private static final String LINE_NUMBERS = "line-numbers";
    private static final String MATCH_BRACES = "match-braces";
    private static final String ICON_MD = "icon-md";
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
        return render(fileName, lines, List.of(marker));
    }

    /**
     * Creates a colorized HTML snippet with the specified source code. Highlights all specified markers and provides
     * clickable and collapsible elements that show the details for each marker.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the source code
     * @param markers
     *         the list of markers to show; if empty, the source code is rendered without any highlights
     *
     * @return the source code as colorized HTML
     */
    String render(final String fileName, final Stream<String> lines, final List<Marker> markers) {
        try (LookaheadStream stream = new LookaheadStream(lines)) {
            if (markers.isEmpty()) {
                StringBuilder all = readBlockUntilLine(stream, Integer.MAX_VALUE);
                String language = selectLanguageClass(fileName, all);
                boolean enableSyntaxHighlighting = shouldEnableSyntaxHighlighting(all, new StringBuilder(), new StringBuilder());
                String code = asCode(all, getCodeClasses(language, enableSyntaxHighlighting));
                return pre().with(new UnescapedText(code)).renderFormatted();
            }

            List<Marker> sortedMarkers = new ArrayList<>(markers);
            sortedMarkers.sort(Comparator.comparingInt(Marker::getLineStart));

            int firstStart = sortedMarkers.get(0).getLineStart();
            StringBuilder firstBefore = readBlockUntilLine(stream, firstStart - 1);
            String language = selectLanguageClass(fileName, firstBefore);

            List<StringBuilder> allBlocks = new ArrayList<>();
            List<Marker> blockMarkers = new ArrayList<>();
            List<Boolean> isMarkedBlock = new ArrayList<>();

            allBlocks.add(firstBefore);
            isMarkedBlock.add(false);
            blockMarkers.add(null);

            int currentLine = firstStart - 1;
            for (int i = 0; i < sortedMarkers.size(); i++) {
                Marker m = sortedMarkers.get(i);
                int mStart = m.getLineStart();
                int mEnd = m.getLineEnd();

                if (mStart <= currentLine) {
                    StringBuilder marked = readBlockUntilLine(stream, mEnd);
                    allBlocks.add(marked);
                    isMarkedBlock.add(true);
                    blockMarkers.add(m);
                    currentLine = mEnd;
                }
                else {
                    StringBuilder gap = readBlockUntilLine(stream, mStart - 1);
                    if (gap.length() > 0) {
                        allBlocks.add(gap);
                        isMarkedBlock.add(false);
                        blockMarkers.add(null);
                    }
                    StringBuilder marked = readBlockUntilLine(stream, mEnd);
                    allBlocks.add(marked);
                    isMarkedBlock.add(true);
                    blockMarkers.add(m);
                    currentLine = mEnd;
                }
            }

            StringBuilder after = readBlockUntilLine(stream, Integer.MAX_VALUE);
            if (after.length() > 0) {
                allBlocks.add(after);
                isMarkedBlock.add(false);
                blockMarkers.add(null);
            }

            int totalLines = allBlocks.stream().mapToInt(this::countLines).sum();
            boolean enableSyntaxHighlighting = totalLines <= MAX_LINES_FOR_SYNTAX_HIGHLIGHTING;

            StringBuilder code = new StringBuilder();
            for (int i = 0; i < allBlocks.size(); i++) {
                if (isMarkedBlock.get(i)) {
                    Marker m = blockMarkers.get(i);
                    code.append(asMarkedCode(allBlocks.get(i), m, getMarkedCodeClasses(language, enableSyntaxHighlighting)));
                    code.append(createInfoPanel(m));
                }
                else {
                    code.append(asCode(allBlocks.get(i), getCodeClasses(language, enableSyntaxHighlighting)));
                }
            }

            return pre().with(new UnescapedText(code.toString())).renderFormatted();
        }
    }

    private boolean shouldEnableSyntaxHighlighting(
            final StringBuilder before, final StringBuilder marked, final StringBuilder after) {
        return countLines(before) + countLines(marked) + countLines(after) <= MAX_LINES_FOR_SYNTAX_HIGHLIGHTING;
    }

    private int countLines(final StringBuilder text) {
        if (text.isEmpty()) {
            return 0;
        }
        return (int) text.chars().filter(c -> c == NEW_LINE).count();
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

    private StringBuilder readBlockUntilLine(final LookaheadStream stream, final int end) {
        StringBuilder marked = new StringBuilder();
        while (stream.hasNext() && stream.getLine() < end) {
            marked.append(stream.next());
            marked.append(NEW_LINE);
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
    private String selectLanguageClass(final String fileName, final StringBuilder before) {
        String extension = StringUtils.substringAfterLast(fileName, ".");

        if ("ts".equals(extension) && Strings.CS.contains(before, QT_LINGUIST_PATTERN)) {
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
