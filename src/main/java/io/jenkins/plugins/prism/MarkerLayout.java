package io.jenkins.plugins.prism;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;

/**
 * Determines where the markers of a source code file are shown. A marker highlights the lines between its start and
 * end line, the client side script of the source code view computes the highlight of each line from the visible
 * markers. The details of a marker (title and description) are shown in a box directly after the last line of the
 * marker, this line is called the anchor line of the marker. Markers without a line (line 0) are shown before the
 * first line (anchor line 0), markers after the end of the file are shown after the last line. Additionally, a marker
 * in a single line can mark some columns of the line.
 *
 * @author Ullrich Hafner
 */
class MarkerLayout {
    private static final String ID_PREFIX = "prism-marker-";

    private final int linesCount;
    private final List<PositionedMarker> markers = new ArrayList<>();
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

        int start = marker.getLineStart();
        if (start < 1) {
            markers.add(new PositionedMarker(marker, id, 0));
        } else if (start > linesCount) {
            markers.add(new PositionedMarker(marker, id, linesCount));
        } else {
            int end = Math.min(Math.max(start, marker.getLineEnd()), linesCount);
            markers.add(new PositionedMarker(marker, id, end));

            if (start == marker.getLineEnd() && marker.getColumnStart() >= 1) {
                columnsPerLine
                        .computeIfAbsent(start, key -> new ArrayList<>())
                        .add(new ColumnRange(
                                marker.getColumnStart(),
                                Math.max(marker.getColumnStart(), marker.getColumnEnd()),
                                List.of(id)));
            }
        }
    }

    /**
     * Returns all markers in the order they should be shown: sorted by their anchor line, and markers with the same
     * anchor line are sorted by their start line.
     *
     * @return the markers
     */
    List<PositionedMarker> getMarkers() {
        return markers.stream()
                .sorted(Comparator.comparingInt(PositionedMarker::anchorLine)
                        .thenComparingInt(positioned -> positioned.marker().getLineStart()))
                .toList();
    }

    /**
     * Returns the column ranges that should be marked in the specified line. Overlapping or adjacent ranges are
     * merged, so the returned ranges are disjoint and sorted by their start column. Each range contains the IDs of
     * all markers that are part of the range.
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
                SortedSet<String> ids = new TreeSet<>(last.markerIds());
                ids.addAll(range.markerIds());
                merged.add(new ColumnRange(last.start(), Math.max(last.end(), range.end()), List.copyOf(ids)));
            } else {
                merged.add(range);
            }
        }
        return merged;
    }

    /**
     * A marker together with the ID of the HTML element that shows the marker and its anchor line.
     *
     * @param marker
     *         the marker
     * @param id
     *         the ID of the HTML element: the ID of the marker or a generated ID if the marker has no ID
     * @param anchorLine
     *         the line after which the box of the marker is shown (0 for markers before the first line)
     */
    record PositionedMarker(Marker marker, String id, int anchorLine) {}

    /**
     * A range of columns in a line that should be marked.
     *
     * @param start
     *         the first column (1-based)
     * @param end
     *         the last column (1-based and inclusive)
     * @param markerIds
     *         the IDs of the markers that mark this range
     */
    record ColumnRange(int start, int end, List<String> markerIds) {}
}
