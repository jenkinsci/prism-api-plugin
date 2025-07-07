package io.jenkins.plugins.prism;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.appearance.AppearanceCategory;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.GlobalConfigurationItem;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Global system configuration for Prism. These configuration options are used globally for all jobs and require
 * administrator permissions.
 *
 * <p>
 * The following settings can be configured:
 * </p>
 *
 * <ul>
 *     <li>
 *      <b>Theme</b>: Prism supports several themes that can be used to adapt the look and feel. You can configure the
 *      default theme used for all Jenkins jobs.
 *     </li>
 * </ul>
 *
 * @author Ullrich Hafner
 */
@Extension
@Symbol("prism")
public class PrismAppearanceConfiguration extends GlobalConfigurationItem {
    private PrismTheme theme = PrismTheme.PRISM;
    private final JenkinsFacade jenkins;

    /**
     * Creates the global configuration and loads the initial values from the corresponding
     * XML file.
     */
    public PrismAppearanceConfiguration() {
        super();

        jenkins =  new JenkinsFacade();

        load();
    }

    @VisibleForTesting
    PrismAppearanceConfiguration(final GlobalConfigurationFacade facade, final JenkinsFacade jenkins) {
        super(facade);

        this.jenkins = jenkins;

        load();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(AppearanceCategory.class);
    }

    /**
     * Returns the singleton instance of this {@link PrismAppearanceConfiguration}.
     *
     * @return the singleton instance
     */
    public static PrismAppearanceConfiguration getInstance() {
        return all().get(PrismAppearanceConfiguration.class);
    }

    /**
     * Sets the active theme to be used when rendering the source code with prism.
     *
     * @param theme
     *         the theme to use
     */
    @DataBoundSetter
    public void setTheme(final PrismTheme theme) {
        this.theme = theme;

        save();
    }

    public PrismTheme getTheme() {
        return theme;
    }

    /**
     * Returns all available themes.
     *
     * @return a model with all available themes
     */
    @POST
    public ListBoxModel doFillThemeItems() {
        ListBoxModel options = new ListBoxModel();
        if (jenkins.hasPermission(Jenkins.ADMINISTER)) {
            options.addAll(PrismTheme.getAllDisplayNames());
        }
        return options;
    }
}
