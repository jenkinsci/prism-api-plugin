package io.jenkins.plugins.prism;

import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.util.JenkinsFacade;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Renders a source code file with Prism syntax highlighting. Optionally, highlights an arbitrary number of markers in
 * the source code: each marker is either a line, some characters in a line, or a multi-line block. The details of
 * each marker (title, description, and icon) are shown in a box after the last line of the marker.
 *
 * <p>
 * The model can be shown as a separate Jenkins view (see {@code index.jelly}), or it can be embedded into the view of
 * another model by including the fragment {@code content.jelly}, e.g.,
 * {@code <st:include it="${it.sourceCode}" page="content.jelly"/>}. The fragment is available for the
 * {@link PermissionDeniedViewModel} as well, so the result of the factory methods can be embedded directly.
 * </p>
 *
 * <p>
 * A marker can be selected using the fragment of the URL: either the ID of the marker (see
 * {@link Marker.MarkerBuilder#withId(String)}) or a line number. A selected marker is highlighted, its description
 * is expanded, and the view scrolls to the marker. If there is only one marker, then this marker is selected
 * automatically.
 * </p>
 *
 * @author Ullrich Hafner
 */
public class SourceCodeViewModel implements ModelObject {
    /**
     * Checks if the current user has permission to view source code.
     *
     * @param owner
     *         the current build as the owner of the view
     * @return {@code true} if the user has permission, {@code false} otherwise
     */
    public static boolean hasPermissionToViewSourceCode(final Run<?, ?> owner) {
        return new JenkinsFacade().hasPermission(Job.WORKSPACE, owner.getParent())
                || !PrismConfiguration.getInstance().isProtectSourceCodeByPermission();
    }

    /**
     * Protects a source code view by checking permissions.
     *
     * @param view
     *         the source code view to protect
     * @param owner
     *         the current build as the owner of the view
     * @param fileName
     *         the name of the file being viewed
     * @return the protected view (either original view or {@link PermissionDeniedViewModel})
     */
    public static ModelObject protectedSourceCodeView(
            final ModelObject view, final Run<?, ?> owner, final String fileName) {
        if (hasPermissionToViewSourceCode(owner)) {
            return view;
        }
        return new PermissionDeniedViewModel(owner, fileName);
    }

    /**
     * Creates a source code view model or a permission-denied view model based on the user's permissions.
     * This is the recommended way to create a view model as it checks permissions before rendering source code.
     *
     * @param owner
     *         the current build as the owner of this view
     * @param fileName
     *         the file name of the shown content
     * @param sourceCodeReader
     *         the source code file to show, provided by a {@link Reader} instance
     * @param marker
     *         a block of lines (or a part of a line) to mark in the source code view
     * @return a {@link SourceCodeViewModel} if permission is granted, or a {@link PermissionDeniedViewModel} otherwise
     */
    public static ModelObject create(
            final Run<?, ?> owner, final String fileName, final Reader sourceCodeReader, final Marker marker) {
        return protectedSourceCodeView(
                new SourceCodeViewModel(owner, fileName, sourceCodeReader, marker), owner, fileName);
    }

    /**
     * Creates a source code view model or a permission-denied view model based on the user's permissions. The view
     * model highlights all specified markers.
     *
     * @param owner
     *         the current build as the owner of this view
     * @param fileName
     *         the file name of the shown content
     * @param sourceCodeReader
     *         the source code file to show, provided by a {@link Reader} instance
     * @param markers
     *         the blocks of lines (or parts of lines) to mark in the source code view, may be empty
     * @return a {@link SourceCodeViewModel} if permission is granted, or a {@link PermissionDeniedViewModel} otherwise
     */
    public static ModelObject create(
            final Run<?, ?> owner, final String fileName, final Reader sourceCodeReader, final List<Marker> markers) {
        return new Builder(owner, fileName).withMarkers(markers).build(sourceCodeReader);
    }

    private final Run<?, ?> owner;
    private final String fileName;
    private final String sourceCode;
    private final boolean collapsible;

    /**
     * Creates a new source code view model instance.
     *
     * @param owner
     *         the current build as the owner of this view
     * @param fileName
     *         the file name of the shown content
     * @param sourceCodeReader
     *         the source code file to show, provided by a {@link Reader} instance
     * @param marker
     *         a block of lines (or a part of a line) to mark in the source code view
     * @deprecated use {@link #create(Run, String, Reader, Marker)} instead
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public SourceCodeViewModel(
            final Run<?, ?> owner, final String fileName, final Reader sourceCodeReader, final Marker marker) {
        this(owner, fileName, sourceCodeReader, List.of(marker));
    }

    private SourceCodeViewModel(
            final Run<?, ?> owner, final String fileName, final Reader sourceCodeReader, final List<Marker> markers) {
        this.owner = owner;
        this.fileName = fileName;
        sourceCode = render(sourceCodeReader, markers);
        collapsible = true;
    }

    private SourceCodeViewModel(
            final Run<?, ?> owner, final String fileName, final String sourceCode, final boolean isCollapsible) {
        this.owner = owner;
        this.fileName = fileName;
        this.sourceCode = sourceCode;
        this.collapsible = isCollapsible;
    }

    public PrismConfiguration getPrismConfiguration() {
        return PrismConfiguration.getInstance();
    }

    private String render(final Reader affectedFile, final List<Marker> markers) {
        try (BufferedReader reader = new BufferedReader(affectedFile)) {
            SourcePrinter sourcePrinter = new SourcePrinter();
            return sourcePrinter.render(fileName, reader.lines(), markers);
        } catch (IOException e) {
            return String.format("%s%n%s", ExceptionUtils.getMessage(e), ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public String getDisplayName() {
        return fileName;
    }

    /**
     * Returns the build as the owner of this view.
     *
     * @return the build
     */
    public Run<?, ?> getOwner() {
        return owner;
    }

    /**
     * Returns the colorized source code.
     *
     * @return the source code
     */
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * Returns whether long sections of lines without markers are collapsed in the view. The lines around the markers
     * are still visible, and the collapsed sections can be expanded by the user.
     *
     * @return {@code true} if the sections without markers are collapsed, {@code false} if all lines are visible
     */
    public boolean isCollapsible() {
        return collapsible;
    }

    /**
     * Creates the view model of a source code view with additional options. By default, the whole content is shown,
     * the content is colorized based on the file name, and the permission to view source code is checked.
     *
     * <p>
     * Example: the excerpt of a console log with some markers, as plain text, without the check of the permission
     * to view the workspace (the console log is not part of the workspace):
     * </p>
     * <pre>{@code
     * var view = new SourceCodeViewModel.Builder(build, "Console Output")
     *         .withMarkers(markers)
     *         .withPlainText()
     *         .withExcerpt(120, 160)
     *         .withoutPermissionCheck()
     *         .build(consoleLines);
     * }</pre>
     */
    public static class Builder {
        private final Run<?, ?> owner;
        private final String fileName;
        private List<Marker> markers = List.of();
        private List<LineAnnotation> annotations = List.of();
        private boolean isPlainText;
        private int firstLine = 1;
        private int lastLine = Integer.MAX_VALUE;
        private boolean isPermissionCheckRequired = true;
        private boolean isCollapsible = true;

        /**
         * Creates a new builder.
         *
         * @param owner
         *         the current build as the owner of the view
         * @param fileName
         *         the file name of the shown content, used to select the language of the syntax highlighting
         */
        public Builder(final Run<?, ?> owner, final String fileName) {
            this.owner = owner;
            this.fileName = fileName;
        }

        /**
         * Defines the markers to show. The lines of the markers refer to the whole content, even if only an excerpt
         * is shown.
         *
         * @param markers
         *         the markers
         *
         * @return this builder
         */
        @SuppressWarnings("checkstyle:HiddenField")
        public Builder withMarkers(final List<Marker> markers) {
            this.markers = List.copyOf(markers);
            return this;
        }

        /**
         * Defines the annotations of the lines. The annotations are shown in a separate column between the line
         * numbers and the code, e.g., to show the number of covered branches of each line. If there are several
         * annotations for the same line, then the last one is shown. Annotations of lines outside the shown content
         * are skipped.
         *
         * @param annotations
         *         the annotations of the lines
         *
         * @return this builder
         */
        @SuppressWarnings("checkstyle:HiddenField")
        public Builder withLineAnnotations(final List<LineAnnotation> annotations) {
            this.annotations = List.copyOf(annotations);
            return this;
        }

        /**
         * Shows the content as plain text, without syntax highlighting.
         *
         * @return this builder
         */
        public Builder withPlainText() {
            isPlainText = true;
            return this;
        }

        /**
         * Shows only an excerpt of the content. The lines keep their line numbers. Markers before the excerpt are
         * shown before its first line, markers after the excerpt after its last line.
         *
         * @param from
         *         the first line of the excerpt (1-based)
         * @param to
         *         the last line of the excerpt (1-based, inclusive)
         *
         * @return this builder
         */
        public Builder withExcerpt(final int from, final int to) {
            firstLine = Math.max(1, from);
            lastLine = Math.max(to, firstLine - 1);
            return this;
        }

        /**
         * Skips the check of the permission to view the source code of the workspace (see
         * {@link SourceCodeViewModel#hasPermissionToViewSourceCode(Run)}). Use this option only for content that is
         * not part of the workspace (e.g., the console log of a build). The caller is responsible to check that the
         * current user is allowed to see the content.
         *
         * @return this builder
         */
        public Builder withoutPermissionCheck() {
            isPermissionCheckRequired = false;
            return this;
        }

        /**
         * Shows all lines of the content. By default, long sections of lines without markers are collapsed, and only
         * the lines around the markers are visible.
         *
         * @return this builder
         */
        public Builder withoutCollapsing() {
            isCollapsible = false;
            return this;
        }

        /**
         * Creates the view model for the content of the specified reader.
         *
         * @param reader
         *         the content to show
         *
         * @return a {@link SourceCodeViewModel}, or a {@link PermissionDeniedViewModel} if the permission is
         *         checked and the user is not allowed to view source code
         */
        public ModelObject build(final Reader reader) {
            if (isDenied()) {
                return new PermissionDeniedViewModel(owner, fileName);
            }
            try (BufferedReader lines = new BufferedReader(reader)) {
                return render(lines.lines());
            } catch (IOException | UncheckedIOException exception) {
                return new SourceCodeViewModel(
                        owner,
                        fileName,
                        String.format(
                                "%s%n%s",
                                ExceptionUtils.getMessage(exception), ExceptionUtils.getStackTrace(exception)),
                        isCollapsible);
            }
        }

        /**
         * Creates the view model for the specified lines.
         *
         * @param lines
         *         the lines of the content to show
         *
         * @return a {@link SourceCodeViewModel}, or a {@link PermissionDeniedViewModel} if the permission is
         *         checked and the user is not allowed to view source code
         */
        public ModelObject build(final Stream<String> lines) {
            if (isDenied()) {
                return new PermissionDeniedViewModel(owner, fileName);
            }
            return render(lines);
        }

        private boolean isDenied() {
            return isPermissionCheckRequired && !hasPermissionToViewSourceCode(owner);
        }

        private SourceCodeViewModel render(final Stream<String> lines) {
            long excerptSize = Math.max(0L, (long) lastLine - firstLine + 1);
            var excerpt = lines.skip(firstLine - 1L).limit(excerptSize);
            return new SourceCodeViewModel(
                    owner,
                    fileName,
                    new SourcePrinter().render(fileName, excerpt, markers, annotations, firstLine, isPlainText),
                    isCollapsible);
        }
    }
}
