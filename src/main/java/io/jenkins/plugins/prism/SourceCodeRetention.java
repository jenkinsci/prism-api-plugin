package io.jenkins.plugins.prism;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import edu.hm.hafner.util.FilteredLog;

import org.jvnet.localizer.LocaleProvider;
import org.jvnet.localizer.Localizable;
import hudson.model.Run;

/**
 * Defines the retention strategy for source code files.
 */
public enum SourceCodeRetention {
    NEVER(new Cleanup(), Messages._SourceCodeRetention_NEVER()),
    LAST_BUILD(new CleanupLast(), Messages._SourceCodeRetention_LAST_BUILD()),
    EVERY_BUILD(new Cleanup(), Messages._SourceCodeRetention_EVERY_BUILD());

    private final Cleanup cleanup;
    private final Localizable localizable;

    SourceCodeRetention(final Cleanup cleanup, final Localizable localizable) {
        this.cleanup = cleanup;
        this.localizable = localizable;
    }

    public String getDisplayName() {
        return localizable.toString(LocaleProvider.getLocale());
    }

    public void cleanup(final Run<?, ?> build, final String directory, final FilteredLog log) {
        cleanup.clean(build, directory, log);
    }

    static class Cleanup {
        void clean(final Run<?, ?> build, final String directory, final FilteredLog log) {
            log.logInfo("Skipping cleaning of source code files in old builds");
        }
    }

    static class CleanupLast extends Cleanup {
        @Override
        void clean(final Run<?, ?> currentBuild, final String directory, final FilteredLog log) {
            for (Run<?, ?> build = currentBuild.getPreviousCompletedBuild(); build != null; build = currentBuild.getPreviousCompletedBuild()) {
                Path buildDir = build.getRootDir().toPath();
                Path sourcesFolder = buildDir.resolve(directory);
                if (Files.exists(sourcesFolder)) {
                    try {
                        FileUtils.deleteDirectory(sourcesFolder.toFile());
                        log.logInfo("Deleting source code files of build " + build.getDisplayName());
                    }
                    catch (IOException exception) {
                        log.logException(exception, "Could not delete source code files of build " + build.getDisplayName());
                    }
                }
            }
        }
    }
}
