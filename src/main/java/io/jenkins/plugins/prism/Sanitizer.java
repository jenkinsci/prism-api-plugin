package io.jenkins.plugins.prism;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.markup.MarkupFormatter;
import hudson.markup.RawHtmlMarkupFormatter;
import j2html.tags.DomContent;
import java.io.IOException;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Sanitizes a piece of unsafe HTML code so that it can be rendered in a UI view. This class serves as simple
 * wrapper for Jenkins´ {@link RawHtmlMarkupFormatter}.
 *
 * @author Ullrich Hafner
 */
public class Sanitizer {
    /** Sanitizes HTML elements in warning messages and tooltips. Use this formatter if raw HTML should be shown. */
    private MarkupFormatter formatter = new RawHtmlMarkupFormatter(true);

    @VisibleForTesting
    void setMarkupFormatter(final MarkupFormatter markupFormatter) {
        formatter = markupFormatter;
    }

    /**
     * Renders the specified HTML code. Removes unsafe HTML constructs.
     *
     * @param html
     *         the HTML to render
     *
     * @return safe HTML
     */
    public String render(final String html) {
        try {
            return formatter.translate(html);
        } catch (IOException exception) {
            return ExceptionUtils.getRootCauseMessage(exception);
        }
    }

    /**
     * Renders the specified HTML code. Removes unsafe HTML constructs.
     *
     * @param text
     *         the text to render
     *
     * @return safe HTML
     */
    public String render(final DomContent text) {
        return render(text.render());
    }
}
