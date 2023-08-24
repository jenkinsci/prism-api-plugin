package io.jenkins.plugins.prism;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.hm.hafner.util.PathUtil;
import edu.hm.hafner.util.VisibleForTesting;

import jenkins.appearance.AppearanceCategory;
import jenkins.model.GlobalConfigurationCategory;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.GlobalConfigurationItem;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Global system configuration for Prism. These configuration options are used globally for all jobs and require
 * administrator permissions.
 * <p>
 * The following settings can be configured:
 * </p>
 *
 * <ul>
 *     <li>
 *      <b>Allowed source code directories</b>: some plugins copy source code files to Jenkins' build folder so that these
 *      files can be rendered in the user interface together with build results (coverage, warnings, etc.).
 *      If these files are not part of the workspace of a build then Jenkins will not show them by default:
 *      otherwise sensitive files could be shown by accident. You can provide a list of additional source code directories
 *      that are allowed to be shown in Jenkins user interface here. Note, that such a directory must be an absolute path
 *      on the agent that executes the build.
 *     </li>
 *     <li>
 *      <b>Theme</b>: Prism supports several themes that can be used to adapt the look and feel. You can configure the
 *      default theme that is used for all Jenkins jobs.
 *     </li>
 * </ul>
 *
 * @author Ullrich Hafner
 */
@Extension
@Symbol("prism")
@SuppressWarnings("PMD.DataClass")
public class PrismConfiguration extends GlobalConfigurationItem {
    private static final PathUtil PATH_UTIL = new PathUtil();

    private List<PermittedSourceCodeDirectory> sourceDirectories = Collections.emptyList();
    private Set<String> normalizedSourceDirectories = Collections.emptySet();
    private PrismTheme theme = PrismTheme.PRISM;
    private final JenkinsFacade jenkins;

    /**
     * Creates the global configuration of source code directories and loads the initial values from the corresponding
     * XML file.
     */
    public PrismConfiguration() {
        super();

        jenkins =  new JenkinsFacade();

        load();
    }

    @VisibleForTesting
    PrismConfiguration(final GlobalConfigurationFacade facade, final JenkinsFacade jenkins) {
        super(facade);

        this.jenkins = jenkins;

        load();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(AppearanceCategory.class);
    }

    @Override
    protected void clearRepeatableProperties() {
        setSourceDirectories(new ArrayList<>());
    }

    /**
     * Returns the singleton instance of this {@link PrismConfiguration}.
     *
     * @return the singleton instance
     */
    public static PrismConfiguration getInstance() {
        return GlobalConfiguration.all().get(PrismConfiguration.class);
    }

    /**
     * Returns the list of allowed source code directories.
     *
     * @return the source root folders
     */
    public List<PermittedSourceCodeDirectory> getSourceDirectories() {
        return sourceDirectories;
    }

    /**
     * Sets the list of source directories to the specified elements. Previously set directories will be removed.
     *
     * @param sourceDirectories
     *         the source directories that contain the affected files
     */
    @DataBoundSetter
    public void setSourceDirectories(final List<PermittedSourceCodeDirectory> sourceDirectories) {
        this.sourceDirectories = new ArrayList<>(sourceDirectories);

        normalizedSourceDirectories = sourceDirectories.stream()
                .map(PermittedSourceCodeDirectory::getPath)
                .map(PATH_UTIL::getAbsolutePath)
                .collect(Collectors.toSet());

        save();
    }

    /**
     * Returns whether the specified director is registered as permitted source code directory.
     *
     * @param sourceDirectory
     *         the source directory to check
     *
     * @return {@code true} if the specified director is registered, {@code false} otherwise
     */
    public boolean isAllowedSourceDirectory(final String sourceDirectory) {
        return normalizedSourceDirectories.contains(PATH_UTIL.getAbsolutePath(sourceDirectory));
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
