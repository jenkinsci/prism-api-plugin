package io.jenkins.plugins.prism;

import edu.hm.hafner.util.PathUtil;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.Symbol;
import hudson.Extension;
import jenkins.model.GlobalConfigurationCategory;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.GlobalConfigurationItem;

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
 *      <b>Allowed source code directories</b>: some plugins copy source code files to Jenkins' build folder so that these
 *      files can be rendered in the user interface together with build results (coverage, warnings, etc.).
 *      If these files are not part of the workspace of a build, then Jenkins will not show them by default:
 *      otherwise sensitive files could be shown by accident. You can provide a list of additional source code directories
 *      that are allowed to be shown in Jenkins user interface here. Note, that such a directory must be an absolute path
 *      on the agent that executes the build.
 *     </li>
 * </ul>
 *
 * @author Ullrich Hafner
 */
@Extension
@Symbol("prism")
public class PrismConfiguration extends GlobalConfigurationItem {
    private static final PathUtil PATH_UTIL = new PathUtil();

    private List<PermittedSourceCodeDirectory> sourceDirectories = Collections.emptyList();
    private Set<String> normalizedSourceDirectories = Collections.emptySet();

    /**
     * Moved to {@link PrismAppearanceConfiguration}.
     */
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "Kept for compatibility")
    private transient PrismTheme theme;

    /**
     * Creates the global configuration of source code directories and loads the initial values from the corresponding
     * XML file.
     */
    public PrismConfiguration() {
        super();

        load();
    }

    @VisibleForTesting
    PrismConfiguration(final GlobalConfigurationFacade facade) {
        super(facade);

        load();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
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
        return all().get(PrismConfiguration.class);
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
     * For maintaining compatibility after the move to {@link PrismAppearanceConfiguration}.
     *
     * @deprecated use {@link PrismAppearanceConfiguration} instead
     * @return a model with the currently selected theme
     */
    @Deprecated
    public PrismTheme getTheme() {
        return PrismAppearanceConfiguration.getInstance().getTheme();
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
}
