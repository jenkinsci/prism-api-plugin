package io.jenkins.plugins.prism;

import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.util.JenkinsFacade;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
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
        return protectedSourceCodeView(
                new SourceCodeViewModel(owner, fileName, sourceCodeReader, markers), owner, fileName);
    }

    private final Run<?, ?> owner;
    private final String fileName;
    private final String sourceCode;

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
}
