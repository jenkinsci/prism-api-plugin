package io.jenkins.plugins.prism;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.PathUtil;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Approved directory that contains source code files that can be shown in Jenkins´ user interface.
 *
 * @author Ullrich Hafner
 */
public class PermittedSourceCodeDirectory implements Describable<PermittedSourceCodeDirectory>, Serializable {
    @Serial
    private static final long serialVersionUID = 4218100811802686066L;

    private final String path;

    /**
     * Creates a new instance of {@link PermittedSourceCodeDirectory}.
     *
     * @param path
     *         the name of the directory
     */
    @DataBoundConstructor
    public PermittedSourceCodeDirectory(final String path) {
        super();

        this.path = path;
    }

    public String getPath() {
        return path;
    }

    /**
     * Descriptor to validate {@link PermittedSourceCodeDirectory}.
     *
     * @author Ullrich Hafner
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<PermittedSourceCodeDirectory> {
        private static final JenkinsFacade JENKINS = new JenkinsFacade();
        private static final PathUtil PATH_UTIL = new PathUtil();

        @NonNull
        @Override
        public String getDisplayName() {
            return StringUtils.EMPTY;
        }

        /**
         * Performs on-the-fly validation on the source code directory.
         *
         * @param path
         *         the relative or absolute path
         *
         * @return the validation result
         */
        @POST
        public FormValidation doCheckPath(@QueryParameter final String path) {
            if (JENKINS.hasPermission(Jenkins.ADMINISTER)
                    && StringUtils.isNotBlank(path)
                    && !PATH_UTIL.isAbsolute(path)) {
                return FormValidation.error("All paths need to be absolute paths on the agent.");
            }
            return FormValidation.ok();
        }
    }
}

