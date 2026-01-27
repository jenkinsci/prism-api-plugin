package io.jenkins.plugins.prism;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;

import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Renders a source code file with Prism syntax highlighting in a separate Jenkins view. Optionally, highlights a marker
 * in the source code: either a line, some characters in a line, or a multi-line block.
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
    public static ModelObject protectedSourceCodeView(final ModelObject view, final Run<?, ?> owner,
            final String fileName) {
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
    public static ModelObject create(final Run<?, ?> owner, final String fileName,
            final Reader sourceCodeReader, final Marker marker) {
        return protectedSourceCodeView(
                new SourceCodeViewModel(owner, fileName, sourceCodeReader, marker),
                owner,
                fileName);
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
    public SourceCodeViewModel(final Run<?, ?> owner, final String fileName, final Reader sourceCodeReader,
            final Marker marker) {
        this.owner = owner;
        this.fileName = fileName;
        sourceCode = render(sourceCodeReader, marker);
    }

    public PrismConfiguration getPrismConfiguration() {
        return PrismConfiguration.getInstance();
    }

    private String render(final Reader affectedFile, final Marker marker) {
        try (BufferedReader reader = new BufferedReader(affectedFile)) {
            SourcePrinter sourcePrinter = new SourcePrinter();
            return sourcePrinter.render(fileName, reader.lines(), marker);
        }
        catch (IOException e) {
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

