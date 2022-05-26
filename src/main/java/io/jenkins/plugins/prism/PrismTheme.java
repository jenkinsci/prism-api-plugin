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

    private String getId() {
        if (this == PrismTheme.PRISM) {
            Theme theme = ThemeManagerPageDecorator.get().findTheme();
            return theme.getProperty("prism-api", "theme").orElse("prism");
        }

        return id;
    }

    public String getFileName() {
        String id = getId();
        if (id.equals("prism")) {
            return "prism.css";
        }

        return "prism-" + getId() + ".css";
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
