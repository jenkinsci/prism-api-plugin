package io.jenkins.plugins.prism;

import io.jenkins.plugins.thememanager.Theme;
import io.jenkins.plugins.thememanager.ThemeManagerPageDecorator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.commons.lang3.exception.ExceptionUtils;

import hudson.model.ModelObject;
import hudson.model.Run;

/**
 * Renders a source code file with Prism syntax highlighting in a separate Jenkins view. Optionally, highlights a marker
 * in the source code: either a line, some characters in a line, or a multi-line block.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public class SourceCodeViewModel implements ModelObject {
    private final Run<?, ?> owner;
    private final String fileName;
    private final String sourceCode;

    /**
     * Creates a new source code view model instance.
     *
     * @param owner
     *         the current build as owner of this view
     * @param fileName
     *         the file name of the shown content
     * @param sourceCodeReader
     *         the source code file to show, provided by a {@link Reader} instance
     * @param marker
     *         a block of lines (or a part of a line) to mark in the source code view
     */
    public SourceCodeViewModel(final Run<?, ?> owner, final String fileName, final Reader sourceCodeReader,
            final Marker marker) {
        this.owner = owner;
        this.fileName = fileName;
        sourceCode = render(sourceCodeReader, marker);
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
     * Returns the build as owner of this view.
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
     * Returns the filename of the prism theme. Themes are stored in the package below the css folder.
     *
     * @return the theme CSS file
     */
    public String getThemeCssFileName() {
        Theme theme = ThemeManagerPageDecorator.get().findTheme();
        return PrismConfiguration.getInstance().getTheme().getFileName(theme);
    }
}

