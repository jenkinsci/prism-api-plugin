package io.jenkins.plugins.prism;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Marks a line, some characters in a line, or a multi-line block in the source code. A marker can optionally be
 * enriched with a message, description, and icon. Additionally, a marker has a severity that defines the color of
 * the marked lines and of the box that shows the details. The box can show a list of badges (short labels like the
 * name of a tool or a category) and a list of links to related pages.
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

    private final Severity severity;
    private final List<Badge> badges;
    private final List<Link> links;
    private final Map<String, String> data;

    @SuppressWarnings("checkstyle:ParameterNumber")
    Marker(
            final String id,
            final String title,
            final String icon,
            final String description,
            final int lineStart,
            final int lineEnd,
            final int columnStart,
            final int columnEnd,
            final Severity severity,
            final List<Badge> badges,
            final List<Link> links,
            final Map<String, String> data) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.columnStart = columnStart;
        this.columnEnd = columnEnd;
        this.severity = severity;
        this.badges = List.copyOf(badges);
        this.links = List.copyOf(links);
        this.data = Map.copyOf(data);
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
     * Returns the severity of this marker. The severity defines the color of the marked lines and of the box that
     * shows the details of the marker.
     *
     * @return the severity
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Returns the badges of this marker. Badges are short labels that are shown in the header of the box, e.g., the
     * name of a tool, a category, or a state like "new".
     *
     * @return the badges, the list may be empty
     */
    public List<Badge> getBadges() {
        return badges;
    }

    /**
     * Returns the links of this marker. Links are shown in the footer of the box and refer to related pages.
     *
     * @return the links, the list may be empty
     */
    public List<Link> getLinks() {
        return links;
    }

    /**
     * Returns additional data of this marker. Each entry is rendered as an attribute {@code data-marker-<name>} of
     * the box of the marker, so client side scripts can use the data, e.g., to filter the markers.
     *
     * @return the data of the marker, the map may be empty
     */
    public Map<String, String> getData() {
        return data;
    }

    /**
     * The severity of a marker. The severity defines the color of the marked lines and of the box that shows the
     * details of the marker. If several markers with different severities cover the same line, then the line is
     * shown with the highest severity.
     */
    public enum Severity {
        /** A severe problem, e.g., an error or a line that is not covered by tests. */
        ERROR,
        /** A problem that should be fixed, e.g., a warning or a partially covered line. */
        WARNING,
        /** An information or a minor problem. */
        INFO;

        /**
         * Returns the name of the CSS modifier for this severity.
         *
         * @return the CSS modifier
         */
        String getCssModifier() {
            return name().toLowerCase(Locale.ENGLISH);
        }

        /**
         * Returns the higher severity of this severity and the specified other severity.
         *
         * @param other
         *         the other severity
         *
         * @return the higher severity
         */
        Severity max(final Severity other) {
            return compareTo(other) <= 0 ? this : other;
        }
    }

    /**
     * A short label that is shown in the header of the box of a marker.
     */
    public static final class Badge {
        private final String text;
        private final String tooltip;

        /**
         * Creates a new badge.
         *
         * @param text
         *         the text of the badge, must not contain HTML tags
         * @param tooltip
         *         the tooltip of the badge, use an empty string if there is no tooltip
         */
        public Badge(final String text, final String tooltip) {
            this.text = text;
            this.tooltip = tooltip;
        }

        public String getText() {
            return text;
        }

        public String getTooltip() {
            return tooltip;
        }
    }

    /**
     * A link to a related page that is shown in the footer of the box of a marker.
     */
    public static final class Link {
        private final String label;
        private final String url;
        private final String icon;

        /**
         * Creates a new link.
         *
         * @param label
         *         the label of the link, must not contain HTML tags
         * @param url
         *         the URL of the link
         * @param icon
         *         the icon of the link (a symbol or the URL of an image), use an empty string if there is no icon
         */
        public Link(final String label, final String url, final String icon) {
            this.label = label;
            this.url = url;
            this.icon = icon;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }

        public String getIcon() {
            return icon;
        }
    }

    /**
     * Creates {@link Marker markers} using the builder pattern.
     *
     * @author Ullrich Hafner
     */
    @SuppressWarnings({"ParameterHidesMemberVariable", "checkstyle:HiddenField"})
    public static class MarkerBuilder {
        private static final Pattern DATA_NAME = Pattern.compile("[a-z][a-z0-9-]*");

        private String id = StringUtils.EMPTY;
        private Severity severity = Severity.WARNING;
        private final List<Badge> badges = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();
        private final Map<String, String> data = new LinkedHashMap<>();
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
         * Defines the severity of the marker. If no severity is defined, then {@link Severity#WARNING} is used.
         *
         * @param severity
         *         the severity of the marker
         *
         * @return this builder
         */
        public MarkerBuilder withSeverity(final Severity severity) {
            this.severity = severity;
            return this;
        }

        /**
         * Adds a badge to the marker. The badges are shown in the order they have been added.
         *
         * @param text
         *         the text of the badge, must not contain HTML tags
         *
         * @return this builder
         */
        public MarkerBuilder withBadge(final String text) {
            return withBadge(text, StringUtils.EMPTY);
        }

        /**
         * Adds a badge with a tooltip to the marker. The badges are shown in the order they have been added.
         *
         * @param text
         *         the text of the badge, must not contain HTML tags
         * @param tooltip
         *         the tooltip of the badge
         *
         * @return this builder
         */
        public MarkerBuilder withBadge(final String text, final String tooltip) {
            badges.add(new Badge(text, tooltip));
            return this;
        }

        /**
         * Adds a link to the marker. The links are shown in the order they have been added.
         *
         * @param label
         *         the label of the link, must not contain HTML tags
         * @param url
         *         the URL of the link
         *
         * @return this builder
         */
        public MarkerBuilder withLink(final String label, final String url) {
            return withLink(label, url, StringUtils.EMPTY);
        }

        /**
         * Adds a link with an icon to the marker. The links are shown in the order they have been added.
         *
         * @param label
         *         the label of the link, must not contain HTML tags
         * @param url
         *         the URL of the link
         * @param icon
         *         the icon of the link (a symbol or the URL of an image)
         *
         * @return this builder
         */
        public MarkerBuilder withLink(final String label, final String url, final String icon) {
            links.add(new Link(label, url, icon));
            return this;
        }

        /**
         * Adds additional data to the marker. The data is rendered as an attribute {@code data-marker-<name>} of the
         * box of the marker, so client side scripts can use the data, e.g., to filter the markers.
         *
         * @param name
         *         the name of the data, must consist of lowercase letters, digits, and hyphens and must start with a
         *         letter
         * @param value
         *         the value of the data
         *
         * @return this builder
         * @throws IllegalArgumentException
         *         if the name is not valid
         */
        public MarkerBuilder withData(final String name, final String value) {
            if (!DATA_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid name of marker data: " + name);
            }
            data.put(name, value);
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

            return new Marker(
                    id,
                    title,
                    icon,
                    description,
                    lineStart,
                    lineEnd,
                    columnStart,
                    columnEnd,
                    severity,
                    badges,
                    links,
                    data);
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
