package io.jenkins.plugins.prism;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;

/**
 * Determines where the markers of a source code file are shown. A marker highlights the lines between its start and
 * end line. The details of a marker (title and description) are shown in a box directly after the last line of the
 * marker. Markers may overlap and several markers may refer to the same line. Markers without a line (line 0) are
 * shown before the first line, markers after the end of the file are shown after the last line.
 *
 * @author Ullrich Hafner
 */
class MarkerLayout {
    private static final String ID_PREFIX = "prism-marker-";

    private final int linesCount;
    private final BitSet markedLines = new BitSet();
    private final Map<Integer, List<PositionedMarker>> markersAfterLine = new TreeMap<>();
    private final Map<Integer, List<ColumnRange>> columnsPerLine = new TreeMap<>();

    /**
     * Creates a new layout for the specified markers.
     *
     * @param markers
     *         the markers to show
     * @param linesCount
     *         the number of lines of the source code file
     */
    MarkerLayout(final List<Marker> markers, final int linesCount) {
        this.linesCount = linesCount;

        for (int index = 0; index < markers.size(); index++) {
            add(markers.get(index), index);
        }
    }

    private void add(final Marker marker, final int index) {
        var id = StringUtils.defaultIfBlank(marker.getId(), ID_PREFIX + (index + 1));
        var positioned = new PositionedMarker(marker, id);

        if (marker.getLineStart() < 1) {
            addMarkerAfterLine(0, positioned);
        } else if (marker.getLineStart() > linesCount) {
            addMarkerAfterLine(linesCount, positioned);
        } else {
            int start = marker.getLineStart();
            int end = Math.min(Math.max(start, marker.getLineEnd()), linesCount);
            markedLines.set(start, end + 1);
            addMarkerAfterLine(end, positioned);

            if (start == marker.getLineEnd() && marker.getColumnStart() >= 1) {
                columnsPerLine
                        .computeIfAbsent(start, key -> new ArrayList<>())
                        .add(new ColumnRange(
                                marker.getColumnStart(), Math.max(marker.getColumnStart(), marker.getColumnEnd())));
            }
        }
    }

    private void addMarkerAfterLine(final int line, final PositionedMarker positioned) {
        markersAfterLine.computeIfAbsent(line, key -> new ArrayList<>()).add(positioned);
    }

    /**
     * Returns whether the specified line is part of at least one marker.
     *
     * @param line
     *         the line to check (1-based)
     *
     * @return {@code true} if the line is marked, {@code false} otherwise
     */
    boolean isMarked(final int line) {
        return markedLines.get(line);
    }

    /**
     * Returns the markers that should be shown after the specified line. The markers are sorted by their start line.
     *
     * @param line
     *         the line (1-based) or 0 for the markers that should be shown before the first line
     *
     * @return the markers to show after the line, or an empty list if there are no such markers
     */
    List<PositionedMarker> getMarkersAfter(final int line) {
        return markersAfterLine.getOrDefault(line, List.of()).stream()
                .sorted(Comparator.comparingInt(
                        positioned -> positioned.marker().getLineStart()))
                .toList();
    }

    /**
     * Returns the column ranges that should be marked in the specified line. Overlapping or adjacent ranges are
     * merged, so the returned ranges are disjoint and sorted by their start column.
     *
     * @param line
     *         the line to check (1-based)
     *
     * @return the column ranges to mark (1-based and inclusive)
     */
    List<ColumnRange> getColumnRanges(final int line) {
        var ranges = columnsPerLine.getOrDefault(line, List.of()).stream()
                .sorted(Comparator.comparingInt(ColumnRange::start))
                .toList();
        var merged = new ArrayList<ColumnRange>();
        for (ColumnRange range : ranges) {
            if (!merged.isEmpty()
                    && range.start() <= merged.get(merged.size() - 1).end() + 1) {
                var last = merged.remove(merged.size() - 1);
                merged.add(new ColumnRange(last.start(), Math.max(last.end(), range.end())));
            } else {
                merged.add(range);
            }
        }
        return merged;
    }

    /**
     * A marker together with the ID of the HTML element that shows the marker.
     *
     * @param marker
     *         the marker
     * @param id
     *         the ID of the HTML element: the ID of the marker or a generated ID if the marker has no ID
     */
    record PositionedMarker(Marker marker, String id) {}

    /**
     * A range of columns in a line that should be marked.
     *
     * @param start
     *         the first column (1-based)
     * @param end
     *         the last column (1-based and inclusive)
     */
    record ColumnRange(int start, int end) {}
}
