package org.jenkinsci.plugins.androidsigning;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;


@Deprecated
public final class Apk extends AbstractDescribableImpl<Apk> {
    private String keyStore;
    private String alias;
    private String apksToSign;
    private boolean archiveSignedApks = true;
    private boolean archiveUnsignedApks = false;

    // renamed fields
    @SuppressWarnings("unused")
    transient private String selection;

    /**
     *
     * @param keyStore an ID of a {@link com.cloudbees.plugins.credentials.common.StandardCertificateCredentials}
     * @param alias the alias of the signing key in the key store
     * @param apksToSign an Ant-style glob pattern; multiple globs separated by commas are allowed
     */
    @DataBoundConstructor
    public Apk(String keyStore, String alias, String apksToSign) {
        this.keyStore = keyStore;
        this.alias = alias;
        this.apksToSign = apksToSign;
    }

    @DataBoundSetter
    public void setArchiveSignedApks(boolean x) {
        archiveSignedApks = x;
    }

    @DataBoundSetter
    public void setArchiveUnsignedApks(boolean x) {
        archiveUnsignedApks = x;
    }

    public Apk archiveSignedApks(boolean x) {
        setArchiveSignedApks(x);
        return this;
    }

    public Apk archiveUnsignedApk(boolean x) {
        setArchiveUnsignedApks(x);
        return this;
    }

    @SuppressWarnings("unused")
    protected Object readResolve() {
        if (apksToSign == null) {
            apksToSign = selection;
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Apk> {
        @Override @Nonnull
        public String getDisplayName() {
            return "APK Signing Entry";
        }

//        @SuppressWarnings("unused")
//        public ListBoxModel doFillKeyStoreItems(@AncestorInPath ItemGroup<?> parent) {
//            if (parent == null) {
//                parent = Jenkins.getInstance();
//            }
//            ListBoxModel items = new ListBoxModel();
//            List<StandardCertificateCredentials> keys = CredentialsProvider.lookupCredentials(
//                StandardCertificateCredentials.class, parent, ACL.SYSTEM, SignApksBuilder.NO_REQUIREMENTS);
//            for (StandardCertificateCredentials key : keys) {
//                items.add(key.getDescription(), key.getId());
//            }
//            return items;
//        }
//
//        @SuppressWarnings("unused")
//        public FormValidation doCheckAlias(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
//            return FormValidation.validateRequired(value);
//        }
//
//        @SuppressWarnings("unused")
//        public FormValidation doCheckApksToSign(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, InterruptedException {
//            if (project == null) {
//                return FormValidation.warning(Messages.validation_noProject());
//            }
//            FilePath someWorkspace = project.getSomeWorkspace();
//            if (someWorkspace != null) {
//                String msg = someWorkspace.validateAntFileMask(value, FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
//                if (msg != null) {
//                    return FormValidation.error(msg);
//                }
//                return FormValidation.ok();
//            }
//            else {
//                return FormValidation.warning(Messages.validation_noWorkspace());
//            }
//        }
    }

    public String getApksToSign() {
        return apksToSign;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public String getAlias() {
        return alias;
    }

    public boolean getArchiveUnsignedApks() {
        return archiveUnsignedApks;
    }

    public boolean getArchiveSignedApks() {
        return archiveSignedApks;
    }

}
