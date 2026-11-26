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
import io.jenkins.plugins.prism.MarkerLayout.ColumnRange;
import io.jenkins.plugins.prism.MarkerLayout.PositionedMarker;
import io.jenkins.plugins.util.JenkinsFacade;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;

/**
 * Renders a source code file and its markers into an HTML snippet. The layout of the source code view is created on
 * the client side by the script {@code SourceCodeViewModel/source-code.js}: the snippet contains the escaped source
 * code as a single code block, so Prism.js can colorize the whole file at once. Afterward, the script splits the
 * colorized code into lines, adds the line numbers, and inserts the boxes of the markers after their anchor lines.
 *
 * <p>
 * The snippet consists of two parts:
 * </p>
 * <ul>
 *     <li>a hidden {@code pre} element with the class {@code prism-source__raw} that contains the source code. The
 *     columns of the markers are enclosed in {@code span} elements with the class {@code code-mark} that list the
 *     IDs of their markers in the attribute {@code data-markers}.</li>
 *     <li>a hidden {@code div} element with the class {@code prism-source__markers} that contains an element for
 *     each marker. The attributes {@code data-line-start}, {@code data-line-end}, and {@code data-anchor-line} define
 *     the position of the marker. Markers without title and description have the class {@code prism-marker--empty}:
 *     they only highlight their lines.</li>
 *     <li>a hidden {@code div} element with the class {@code prism-source__annotations} that contains an element for
 *     each {@link LineAnnotation annotation} of a line. The attribute {@code data-line} defines the line of the
 *     annotation. The element is part of the snippet only if there are annotations.</li>
 * </ul>
 *
 * @author Philippe Arteau
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.GodClass")
class SourcePrinter {
    private static final Sanitizer SANITIZER = new Sanitizer();

    private static final String QT_LINGUIST_PATTERN = "<!DOCTYPE TS>";
    private static final int MAX_LINES_FOR_SYNTAX_HIGHLIGHTING = 5_000;
    private static final String ICON_MD = "icon-md";
    private static final String ICON_SM = "icon-sm";
    private static final String MARKER = "prism-marker";
    private static final String MARKER_HEADER = "prism-marker__header";
    private static final String MARKER_ICON = "prism-marker__icon";
    private static final String HIDDEN = "hidden";
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
     * Creates an HTML snippet with the specified source code and marker.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the source code
     * @param marker
     *         the marker to show
     *
     * @return the source code and the marker as HTML snippet
     */
    String render(final String fileName, final Stream<String> lines, final Marker marker) {
        return render(fileName, lines, List.of(marker));
    }

    /**
     * Creates an HTML snippet with the specified source code and markers. Markers may overlap, and several markers
     * may refer to the same line.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the source code
     * @param markers
     *         the markers to show, the list may be empty
     *
     * @return the source code and the markers as HTML snippet
     */
    String render(final String fileName, final Stream<String> lines, final List<Marker> markers) {
        return render(fileName, lines, markers, 1, false);
    }

    /**
     * Creates an HTML snippet with an excerpt of the specified source code and markers. Markers may overlap, and
     * several markers may refer to the same line.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the excerpt
     * @param markers
     *         the markers to show, the list may be empty. The lines of the markers refer to the whole file, not to the
     *         excerpt.
     * @param firstLine
     *         the line number of the first line of the excerpt (1-based)
     * @param isPlainText
     *         determines whether the content is plain text that should not be colorized
     *
     * @return the source code and the markers as HTML snippet
     */
    String render(
            final String fileName,
            final Stream<String> lines,
            final List<Marker> markers,
            final int firstLine,
            final boolean isPlainText) {
        return render(fileName, lines, markers, List.of(), firstLine, isPlainText);
    }

    /**
     * Creates an HTML snippet with an excerpt of the specified source code, markers, and annotations of the lines.
     * Markers may overlap, and several markers may refer to the same line.
     *
     * @param fileName
     *         the file name of the source code file
     * @param lines
     *         the lines of the excerpt
     * @param markers
     *         the markers to show, the list may be empty. The lines of the markers refer to the whole file, not to the
     *         excerpt.
     * @param annotations
     *         the annotations of the lines, the list may be empty. If there are several annotations for the same
     *         line, then the last one is shown. Annotations of lines outside the excerpt are skipped.
     * @param firstLine
     *         the line number of the first line of the excerpt (1-based)
     * @param isPlainText
     *         determines whether the content is plain text that should not be colorized
     *
     * @return the source code, the markers, and the annotations as HTML snippet
     */
    String render(
            final String fileName,
            final Stream<String> lines,
            final List<Marker> markers,
            final List<LineAnnotation> annotations,
            final int firstLine,
            final boolean isPlainText) {
        List<String> source = lines.toList();
        int lastLine = firstLine + source.size() - 1;
        var layout = new MarkerLayout(markers, firstLine, lastLine);

        var code = new StringBuilder();
        for (int index = 0; index < source.size(); index++) {
            if (index > 0) {
                code.append(NEW_LINE);
            }
            code.append(renderLine(source.get(index), layout.getColumnRanges(firstLine + index)));
        }

        var codeElement = code().attr("data-lines", source.size())
                .attr("data-first-line", firstLine)
                .with(new UnescapedText(code.toString()));
        if (!isPlainText && source.size() <= MAX_LINES_FOR_SYNTAX_HIGHLIGHTING) {
            var language = selectLanguageClass(fileName, source);
            if (!language.isEmpty()) {
                codeElement.withClass(language);
            }
        }
        var raw = pre().withClass("prism-source__raw").attr(HIDDEN, HIDDEN).with(codeElement);
        var markerElements = div().withClass("prism-source__markers")
                .attr(HIDDEN, HIDDEN)
                .with(each(layout.getMarkers(), this::createMarker));

        return raw.render() + markerElements.render() + renderAnnotations(annotations, firstLine, lastLine);
    }

    private String renderAnnotations(final List<LineAnnotation> annotations, final int firstLine, final int lastLine) {
        var annotationPerLine = new TreeMap<Integer, LineAnnotation>();
        annotations.stream()
                .filter(annotation -> annotation.line() >= firstLine && annotation.line() <= lastLine)
                .forEach(annotation -> annotationPerLine.put(annotation.line(), annotation));
        if (annotationPerLine.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return div().withClass("prism-source__annotations")
                .attr(HIDDEN, HIDDEN)
                .with(each(annotationPerLine.values(), this::createAnnotation))
                .render();
    }

    private DomContent createAnnotation(final LineAnnotation annotation) {
        var element = span().withClass("prism-line__annotation")
                .attr("data-line", annotation.line())
                .withText(annotation.text());
        if (StringUtils.isNotBlank(annotation.tooltip())) {
            element.attr("tooltip", annotation.tooltip());
        }
        return element;
    }

    /**
     * Escapes the specified line and encloses the marked columns with {@code span} elements.
     *
     * @param text
     *         the text of the line
     * @param ranges
     *         the disjoint column ranges to mark, sorted by their start column
     *
     * @return the line as HTML
     */
    private String renderLine(final String text, final List<ColumnRange> ranges) {
        var html = new StringBuilder();
        int position = 0;
        for (ColumnRange range : ranges) {
            int start = range.start() - 1;
            int end = Math.min(range.end(), text.length());
            if (start < position || start >= end) {
                continue;
            }
            html.append(escape(text.substring(position, start)));
            html.append(span().withClass("code-mark")
                    .attr("data-markers", String.join(" ", range.markerIds()))
                    .with(new UnescapedText(escape(text.substring(start, end))))
                    .render());
            position = end;
        }
        html.append(escape(text.substring(position)));
        return html.toString();
    }

    private String escape(final String text) {
        return StringEscapeUtils.escapeHtml4(text);
    }

    private boolean hasText(final Marker marker) {
        return StringUtils.isNotBlank(marker.getTitle()) || StringUtils.isNotBlank(marker.getDescription());
    }

    private DomContent createMarker(final PositionedMarker positioned) {
        var marker = positioned.marker();
        var severityClass = MARKER + "--" + marker.getSeverity().getCssModifier();
        var element = hasText(marker)
                ? div().withClasses(MARKER, severityClass)
                : div().withClasses(MARKER, severityClass, MARKER + "--empty");
        element.withId(positioned.id())
                .attr("data-line-start", marker.getLineStart())
                .attr("data-line-end", marker.getLineEnd())
                .attr("data-anchor-line", positioned.anchorLine())
                .attr("data-severity", marker.getSeverity().getCssModifier());
        marker.getData().forEach((name, value) -> element.attr("data-marker-" + name, value));
        if (!hasText(marker)) {
            return element;
        }
        if (StringUtils.isBlank(marker.getDescription())) {
            element.with(div().withClass(MARKER_HEADER).with(createHeader(marker)));
        } else {
            element.with(details()
                    .withClass("prism-marker__details")
                    .with(
                            summary().withClass(MARKER_HEADER).with(createHeader(marker)),
                            div().withClass("prism-marker__description").with(unescape(marker.getDescription()))));
        }
        var links = marker.getLinks().stream().filter(this::isSafe).toList();
        if (!links.isEmpty()) {
            element.with(div().withClass("prism-marker__links").with(each(links, this::createLink)));
        }
        return element;
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
        if (StringUtils.isNotBlank(badge.getUrl()) && isSafe(badge.getUrl())) {
            var link = a().withClasses("prism-marker__badge", "prism-marker__badge--link")
                    .withHref(badge.getUrl())
                    .withTarget("_blank")
                    .withRel("noopener noreferrer")
                    .withText(badge.getText());
            if (StringUtils.isNotBlank(badge.getTooltip())) {
                link.attr("tooltip", badge.getTooltip());
            }
            return link;
        }
        var element = span().withClass("prism-marker__badge").withText(badge.getText());
        if (StringUtils.isNotBlank(badge.getTooltip())) {
            element.attr("tooltip", badge.getTooltip());
        }
        return element;
    }

    private boolean isSafe(final Link link) {
        return isSafe(link.getUrl());
    }

    private boolean isSafe(final String url) {
        return !Strings.CI.startsWith(StringUtils.strip(url), "javascript:");
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

    /**
     * Selects the Prism language of the specified file.
     *
     * @param fileName
     *         the name of the file
     * @param lines
     *         the lines of the file
     *
     * @return the CSS class of the language, or an empty string for plain text files that should not be colorized
     */
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
            case "log", "txt" -> ""; // Plain text, no syntax highlighting
            default -> "language-clike"; // Best effort for unknown extensions
        };
    }
}
