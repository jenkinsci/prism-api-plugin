package io.jenkins.plugins.prism;

import org.apache.commons.lang3.StringUtils;

/**
 * Marks a line, some characters in a line, or a multi-line block in the source code. A marker can optionally be
 * enriched with a message, description, and icon.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.DataClass")
public class Marker {
    private final String id;
    private final String title;
    private final String icon;
    private final String description;

    private final int lineStart;
    private final int lineEnd;
    private final int columnStart;
    private final int columnEnd;

    @SuppressWarnings("checkstyle:ParameterNumber")
    Marker(
            final String id,
            final String title,
            final String icon,
            final String description,
            final int lineStart,
            final int lineEnd,
            final int columnStart,
            final int columnEnd) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.columnStart = columnStart;
        this.columnEnd = columnEnd;
    }

    /**
     * Returns the ID of this marker. The ID is used as ID of the HTML element that shows the marker in the source
     * code view, so it can be used as fragment of a URL to select and scroll to the marker.
     *
     * @return the ID of the marker, or an empty string if no ID has been defined
     */
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public int getLineStart() {
        return lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }

    public int getColumnStart() {
        return columnStart;
    }

    public int getColumnEnd() {
        return columnEnd;
    }

    /**
     * Creates {@link Marker markers} using the builder pattern.
     *
     * @author Ullrich Hafner
     */
    @SuppressWarnings({"ParameterHidesMemberVariable", "checkstyle:HiddenField"})
    public static class MarkerBuilder {
        private String id = StringUtils.EMPTY;
        private String title = StringUtils.EMPTY;
        private String icon = StringUtils.EMPTY;
        private String description = StringUtils.EMPTY;
        private int lineStart;
        private int lineEnd;
        private int columnStart;
        private int columnEnd;

        /**
         * Defines the ID of the marker. The ID is used as ID of the HTML element that shows the marker in the source
         * code view, so it must be unique within a source code view. If no ID is defined, then a unique ID is
         * generated when the source code is rendered.
         *
         * @param id
         *         the ID of the marker
         *
         * @return this builder
         */
        public MarkerBuilder withId(final String id) {
            this.id = id;
            return this;
        }

        /**
         * Defines the title of the marker. This title must not contain HTML tags.
         *
         * @param title
         *         the title, should fit into a single line
         *
         * @return this builder
         */
        public MarkerBuilder withTitle(final String title) {
            this.title = title;
            return this;
        }

        /**
         * Defines the icon of the marker.
         *
         * @param icon
         *         the icon (if available use an SVG icon)
         *
         * @return this builder
         */
        public MarkerBuilder withIcon(final String icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Defines the title of the marker.
         *
         * @param description
         *         the detailed description of the maker. This description may contain valid HTML elements.
         *
         * @return this builder
         */
        public MarkerBuilder withDescription(final String description) {
            this.description = description;
            return this;
        }

        /**
         * Defines the first line of this marker (lines start at 1; 0 indicates the whole file).
         *
         * @param lineStart
         *         the first line
         *
         * @return this builder
         */
        public MarkerBuilder withLineStart(final int lineStart) {
            this.lineStart = lineStart;
            return this;
        }

        /**
         * Defines the last line of this marker (lines start at 1).
         *
         * @param lineEnd
         *         the last line
         *
         * @return this builder
         */
        public MarkerBuilder withLineEnd(final int lineEnd) {
            this.lineEnd = lineEnd;
            return this;
        }

        /**
         * Defines the first column of this marker (columns start at 1, 0 indicates the whole line).
         *
         * @param columnStart
         *         the first column
         *
         * @return this builder
         */
        public MarkerBuilder withColumnStart(final int columnStart) {
            this.columnStart = columnStart;
            return this;
        }

        /**
         * Defines the last column of this marker (columns start at 1).
         *
         * @param columnEnd
         *         the last column
         *
         * @return this builder
         */
        public MarkerBuilder withColumnEnd(final int columnEnd) {
            this.columnEnd = columnEnd;
            return this;
        }

        /**
         * Creates an immutable {@link Marker} instance using the configured properties.
         *
         * @return the new annotation
         */
        public Marker build() {
            int providedLineStart = defaultInteger(lineStart);
            int providedLineEnd = defaultInteger(lineEnd) == 0 ? providedLineStart : defaultInteger(lineEnd);
            if (providedLineStart == 0) {
                this.lineStart = providedLineEnd;
                this.lineEnd = providedLineEnd;
            } else {
                this.lineStart = Math.min(providedLineStart, providedLineEnd);
                this.lineEnd = Math.max(providedLineStart, providedLineEnd);
            }

            int providedColumnStart = defaultInteger(columnStart);
            int providedColumnEnd = defaultInteger(columnEnd) == 0 ? providedColumnStart : defaultInteger(columnEnd);
            if (providedColumnStart == 0) {
                this.columnStart = providedColumnEnd;
                this.columnEnd = providedColumnEnd;
            } else {
                this.columnStart = Math.min(providedColumnStart, providedColumnEnd);
                this.columnEnd = Math.max(providedColumnStart, providedColumnEnd);
            }

            return new Marker(id, title, icon, description, lineStart, lineEnd, columnStart, columnEnd);
        }

        /**
         * Creates a default Integer representation for undefined input parameters.
         *
         * @param integer
         *         the integer to check
         *
         * @return the valid string or a default string if the specified string is not valid
         */
        private int defaultInteger(final int integer) {
            return Math.max(integer, 0);
        }
    }
}
