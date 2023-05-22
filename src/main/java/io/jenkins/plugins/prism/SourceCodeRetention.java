package io.jenkins.plugins.prism;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import edu.hm.hafner.util.FilteredLog;

import org.jvnet.localizer.LocaleProvider;
import org.jvnet.localizer.Localizable;
import hudson.model.Run;
import hudson.util.ListBoxModel;

/**
 * Defines the retention strategy for source code files.
 */
public enum SourceCodeRetention {
    /** Never store source code files. */
    NEVER(new CleanupOldBuilds(), Messages._SourceCodeRetention_NEVER()),
    /** Store source code files of the last build, delete older artifacts. */
    LAST_BUILD(new CleanupOldBuilds(), Messages._SourceCodeRetention_LAST_BUILD()),
    /** Store source code files for all builds, never delete those files automatically. */
    EVERY_BUILD(new Cleanup(), Messages._SourceCodeRetention_EVERY_BUILD()),
    /** Store only changed source code files for all builds, never delete those files automatically. */
    MODIFIED(new Cleanup(), Messages._SourceCodeRetention_MODIFIED());

    /**
     * Returns all available {@link SourceCodeRetention} strategies as a {@link ListBoxModel}.
     *
     * @return all strategies
     */
    public static ListBoxModel fillItems() {
        ListBoxModel items = new ListBoxModel();
        for (SourceCodeRetention retention : values()) {
            items.add(retention.getDisplayName(), retention.name());
        }
        return items;
    }

    @SuppressWarnings("PMD.NonSerializableClass")
    private final Cleanup cleanupStrategy;
    private final Localizable localizable;

    SourceCodeRetention(final Cleanup cleanupStrategy, final Localizable localizable) {
        this.cleanupStrategy = cleanupStrategy;
        this.localizable = localizable;
    }

    public String getDisplayName() {
        return localizable.toString(LocaleProvider.getLocale());
    }

    /**
     * Cleanup the stored source code files of previous builds.
     *
     * @param build
     *         starting with this build, all previous builds will be scanned for source code files that can be deleted
     * @param directory
     *         the directory, where the source code files are stored within each build
     * @param log
     *         logger
     */
    public void cleanup(final Run<?, ?> build, final String directory, final FilteredLog log) {
        cleanupStrategy.clean(build, directory, log);
    }

    static class Cleanup {
        void clean(final Run<?, ?> build, final String directory, final FilteredLog log) {
            log.logInfo("Skipping cleaning of source code files in old builds");
        }
    }

    static class CleanupOldBuilds extends Cleanup {
        @Override
        void clean(final Run<?, ?> currentBuild, final String directory, final FilteredLog log) {
            for (Run<?, ?> build = currentBuild.getPreviousCompletedBuild();
                    build != null; build = build.getPreviousCompletedBuild()) {
                Path buildDir = build.getRootDir().toPath();
                Path sourcesFolder = buildDir.resolve(directory);
                if (Files.exists(sourcesFolder)) {
                    try {
                        FileUtils.deleteDirectory(sourcesFolder.toFile());
                        log.logInfo("Deleting source code files of build " + build.getDisplayName());
                    }
                    catch (IOException exception) {
                        log.logException(exception,
                                "Could not delete source code files of build " + build.getDisplayName());
                    }
                }
            }
        }
    }
}
