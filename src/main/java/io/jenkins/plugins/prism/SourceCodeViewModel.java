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
    private final Run<?, ?> owner;
    private final String fileName;
    private final String sourceCode;

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
        if (new JenkinsFacade().hasPermission(Job.WORKSPACE, owner.getParent())) {
            return new SourceCodeViewModel(owner, fileName, sourceCodeReader, marker);
        }
        else {
            return new PermissionDeniedViewModel(owner, fileName);
        }
    }

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

