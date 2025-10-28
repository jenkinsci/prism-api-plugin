package io.jenkins.plugins.prism;

import org.apache.commons.lang3.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;

import io.jenkins.plugins.util.JenkinsFacade;

/**
 * A relative or absolute directory that contains source code files that can be shown in Jenkins´ user interface.
 *
 * @author Ullrich Hafner
 */
public final class SourceCodeDirectory implements Describable<SourceCodeDirectory>, Serializable {
    @Serial
    private static final long serialVersionUID = -9108340338391937915L;

    private final String path;

    /**
     * Creates a new instance of {@link SourceCodeDirectory}.
     *
     * @param path
     *         the name of the directory
     */
    @DataBoundConstructor
    public SourceCodeDirectory(final String path) {
        super();

        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourceCodeDirectory that = (SourceCodeDirectory) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    /**
     * Descriptor to validate {@link SourceCodeDirectory}.
     *
     * @author Ullrich Hafner
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<SourceCodeDirectory> {
        private static final JenkinsFacade JENKINS = new JenkinsFacade();
        private static final SourceDirectoryValidation VALIDATION = new SourceDirectoryValidation();

        @NonNull
        @Override
        public String getDisplayName() {
            return StringUtils.EMPTY;
        }

        /**
         * Performs on-the-fly validation of the source code directory.
         *
         * @param project
         *         the project that is configured
         * @param path
         *         the relative or absolute path
         *
         * @return the validation result
         */
        @POST
        public FormValidation doCheckPath(@AncestorInPath final AbstractProject<?, ?> project,
                @QueryParameter final String path) {
            if (!JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FormValidation.ok();
            }

            return VALIDATION.validateSourceDirectory(project, path);
        }
    }
}

