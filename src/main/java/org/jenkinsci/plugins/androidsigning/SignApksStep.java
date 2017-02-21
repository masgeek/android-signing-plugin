package org.jenkinsci.plugins.androidsigning;

import com.google.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;


public class SignApksStep extends AbstractStepImpl {

    @CheckForNull
    private String keyStoreId;
    @CheckForNull
    private String keyAlias;
    @CheckForNull
    private String apksToSign;
    private boolean archiveSignedApks = true;
    private boolean archiveUnsignedApks = false;

    @DataBoundConstructor
    public SignApksStep() {
    }

    @DataBoundSetter
    public void setKeyStoreId(String x) {
        keyStoreId = x;
    }

    @DataBoundSetter
    public void setKeyAlias(String x) {
        keyAlias = x;
    }

    @DataBoundSetter
    public void setApksToSign(String x) {
        apksToSign = x;
    }

    @DataBoundSetter
    public void setArchiveSignedApks(boolean x) {
        archiveSignedApks = x;
    }

    @DataBoundSetter
    public void setArchiveUnsignedApks(boolean x) {
        archiveUnsignedApks = x;
    }

    public String getKeyStoreId() {
        return keyStoreId;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getApksToSign() {
        return apksToSign;
    }

    public boolean getArchiveSignedApks() {
        return archiveSignedApks;
    }

    public boolean getArdhiveUnsigedApks() {
        return archiveUnsignedApks;
    }


    private static class SignApksStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient SignApksStep step;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient Run build;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient FilePath workspace;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient Launcher launcher;

        @StepContextParameter
        @SuppressWarnings("unused")
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {
            Apk signingEntry = new Apk(
                step.getKeyStoreId(), step.getKeyAlias(), step.getApksToSign())
                    .archiveSignedApks(step.getArchiveSignedApks())
                    .archiveUnsignedApk(step.getArdhiveUnsigedApks());
            SignApksBuilder builder = new SignApksBuilder(Collections.singletonList(signingEntry));
            builder.perform(build, workspace, launcher, listener);
            return null;
        }
    }

    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(SignApksStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "signAndroidApks";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.builderDisplayName();
        }
    }
}
