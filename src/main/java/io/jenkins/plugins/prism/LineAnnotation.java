package io.jenkins.plugins.prism;

import org.apache.commons.lang3.StringUtils;

/**
 * A short text that is shown in a separate column between the line number and the code of a line, e.g., the number of
 * covered branches of the line. The column is shown only if at least one line of the source code view has an
 * annotation. See {@link SourceCodeViewModel.Builder#withLineAnnotations(java.util.List)}.
 *
 * @param line
 *         the line of the annotation (1-based)
 * @param text
 *         the text of the annotation, must not contain HTML tags (the text will be escaped)
 * @param tooltip
 *         the tooltip of the annotation, use an empty string if there is no tooltip
 *
 * @author Ullrich Hafner
 */
public record LineAnnotation(int line, String text, String tooltip) {
    /**
     * Creates a new annotation without a tooltip.
     *
     * @param line
     *         the line of the annotation (1-based)
     * @param text
     *         the text of the annotation, must not contain HTML tags (the text will be escaped)
     */
    public LineAnnotation(final int line, final String text) {
        this(line, text, StringUtils.EMPTY);
    }
}
