package io.jenkins.plugins.prism;

import hudson.util.ListBoxModel;
import io.jenkins.plugins.thememanager.Theme;
import io.jenkins.plugins.thememanager.ThemeManagerPageDecorator;

/**
 * Defines the active theme to be used when rendering the source code with Prism.
 *
 * @author Ullrich Hafner
 */
public enum PrismTheme {
    PRISM("Recommended", null),
    COY("Coy", "coy"),
    DARK("Dark", "dark"),
    FUNKY("Funky", "funky"),
    OKAIDIA("Okaidia", "okaidia"),
    SOLARIZED_LIGHT("Solarized Light", "solarizedlight"),
    TOMORROW_NIGHT("Tomorrow Night", "tomorrow"),
    TWILIGHT("Twilight", "twilight");

    private final String displayName;
    private final String id;

    PrismTheme(final String displayName, final String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    private String getId(Theme theme) {
        if (this == PrismTheme.PRISM) {
            return theme.getProperty("prism-api", "theme").orElse("prism");
        }

        return id;
    }

    /**
     * File name for the CSS to load for the prism theme.
     * @param theme the currently active theme from theme manager plugin
     * @return relative file path to the theme to load
     */
    public String getFileName(Theme theme) {
        String themeId = getId(theme);
        if ("prism".equals(themeId)) {
            return "prism.css";
        }

        return "prism-" + themeId + ".css";
    }

    /**
     * Use {@link #getFileName(Theme)} instead.
     * You probably shouldn't be calling this directly though and should be calling
     * {@link PrismConfiguration#getThemeCssFileName()}.
     */
    @Deprecated
    public String getFileName() {
        Theme theme = ThemeManagerPageDecorator.get().findTheme();
        return getFileName(theme);
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
