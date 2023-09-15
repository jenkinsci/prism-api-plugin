package io.jenkins.plugins.prism;

import hudson.util.ListBoxModel;

/**
 * Defines the active theme to be used when rendering the source code with Prism.
 *
 * @author Ullrich Hafner
 */
public enum PrismTheme {
    PRISM("Default (light and dark mode)", "prism.css"),
    COY("Coy", "prism-coy.css"),
    DARK("Dark", "prism-dark.css"),
    FUNKY("Funky", "prism-funky.css"),
    OKAIDIA("Okaidia", "prism-okaidia.css"),
    SOLARIZED_LIGHT("Solarized Light", "prism-solarizedlight.css"),
    TOMORROW_NIGHT("Tomorrow Night", "prism-tomorrow.css"),
    TWILIGHT("Twilight", "prism-twilight.css");

    private final String displayName;
    private final String fileName;

    PrismTheme(final String displayName, final String fileName) {
        this.displayName = displayName;
        this.fileName = fileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * Returns all available themes in a {@link ListBoxModel}.
     *
     * @return the themes as an model
     */
    public static ListBoxModel getAllDisplayNames() {
        ListBoxModel model = new ListBoxModel();
        for (PrismTheme theme : values()) {
            model.add(theme.getDisplayName(), theme.name());
        }
        return model;
    }
}
