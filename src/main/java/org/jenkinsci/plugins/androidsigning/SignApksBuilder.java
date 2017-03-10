/*
 ===========================================================
 Apache License, Version 2.0 - Derivative Work modified file
 -----------------------------------------------------------
 This file has been modified by BIT Systems.
 All modifications are copyright (c) BIT Systems, 2016.
 ===========================================================

 This file was originally named SignArtifactsPlugin.java.  The contents of this
 file have been signifcantly modified from the original Work contents.
 */

package org.jenkinsci.plugins.androidsigning;

import com.android.apksig.ApkSigner;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;

public class SignApksBuilder extends Builder implements SimpleBuildStep {

    static final List<DomainRequirement> NO_REQUIREMENTS = Collections.emptyList();
    static final String BUILDER_DIR = SignApksBuilder.class.getSimpleName() + "-out";

    static List<SignApksBuilder> singleEntryBuildersFromEntriesOfBuilder(SignApksBuilder oldBuilder) {
        List<SignApksBuilder> signers = new ArrayList<>(oldBuilder.getEntries().size());
        for (Apk apk : oldBuilder.getEntries()) {
            SignApksBuilder b = new SignApksBuilder();
            b.setPropertiesFromOldSigningEntry(apk);
            b.setAndroidHome(oldBuilder.getAndroidHome());
            b.setZipalignPath(oldBuilder.getZipalignPath());
            signers.add(b);
        }
        return signers;
    }

    private String androidHome;
    private String zipalignPath;
    private String keyStoreId;
    private String keyAlias;
    private String apksToSign;
    private boolean archiveSignedApks = true;
    private boolean archiveUnsignedApks = false;
    private boolean skipZipalign = false;

    transient private List<Apk> entries;

    @Deprecated
    public SignApksBuilder(List<Apk> entries) {
        if (entries.size() == 1) {
            setPropertiesFromOldSigningEntry(entries.get(0));
        }
        else if (entries.size() > 1) {
            throw new UnsupportedOperationException("this constructor is deprecated; use multiple build steps instead of multiple signing entries");
        }
    }

    @DataBoundConstructor
    public SignApksBuilder() {
    }

    private void setPropertiesFromOldSigningEntry(Apk entry) {
        setKeyStoreId(entry.getKeyStore());
        setKeyAlias(entry.getAlias());
        setApksToSign(entry.getApksToSign());
        setArchiveSignedApks(entry.getArchiveSignedApks());
        setArchiveUnsignedApks(entry.getArchiveUnsignedApks());
    }
    
    private boolean isIntermediateFailure(Run build) {
        // TODO: does this work in pipeline?
        Result result = build.getResult();
        return result != null && result.isWorseThan(Result.UNSTABLE);
    }

    private String[] getSelectionGlobs() {
        String[] globs = getApksToSign().split("\\s*,\\s*");
        List<String> cleanGlobs = new ArrayList<>(globs.length);
        for (String glob : globs) {
            glob = glob.trim();
            if (glob.length() > 0) {
                cleanGlobs.add(glob);
            }
        }
        return cleanGlobs.toArray(new String[cleanGlobs.size()]);
    }

    boolean isMigrated() {
        return entries == null || entries.isEmpty();
    }

    @Deprecated
    public List<Apk> getEntries() {
        return entries;
    }

    @DataBoundSetter
    public void setAndroidHome(String x) {
        androidHome = StringUtils.stripToNull(x);
    }

    public String getAndroidHome() {
        return androidHome;
    }

    @DataBoundSetter
    public void setZipalignPath(String x) {
        zipalignPath = StringUtils.stripToNull(x);
    }

    public String getZipalignPath() {
        return zipalignPath;
    }

    @DataBoundSetter
    public void setKeyStoreId(String x) {
        keyStoreId = x;
    }

    public String getKeyStoreId() {
        return keyStoreId;
    }

    @DataBoundSetter
    public void setKeyAlias(String x) {
        keyAlias = x;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    @DataBoundSetter
    public void setApksToSign(String x) {
        apksToSign = x;
    }

    public String getApksToSign() {
        return apksToSign;
    }

    @DataBoundSetter
    public void setArchiveSignedApks(boolean x) {
        archiveSignedApks = x;
    }

    public boolean getArchiveSignedApks() {
        return archiveSignedApks;
    }

    @DataBoundSetter
    public void setArchiveUnsignedApks(boolean x) {
        archiveUnsignedApks = x;
    }

    public boolean getArchiveUnsignedApks() {
        return archiveUnsignedApks;
    }

    @DataBoundSetter
    public void setSkipZipalign(boolean x) {
        skipZipalign = x;
    }

    public boolean getSkipZipalign() {
        return skipZipalign;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        if (isIntermediateFailure(run)) {
            listener.getLogger().println("[SignApksBuilder] skipping Sign APKs step because a previous step failed");
            return;
        }

        if (getEntries() != null && !getEntries().isEmpty()) {
            List<SignApksBuilder> newModelBuilders = singleEntryBuildersFromEntriesOfBuilder(this);
            for (SignApksBuilder builder : newModelBuilders) {
                builder.perform(run, workspace, launcher, listener);
            }
            return;
        }

        EnvVars env;
        if (run instanceof AbstractBuild) {
            env = run.getEnvironment(listener);
            env.overrideAll(((AbstractBuild<?,?>) run).getBuildVariables());
        }
        else {
            env = new EnvVars();
        }

        FilePath builderDir = workspace.child(BUILDER_DIR);
        String excludeBuilderDir = builderDir.getName() + "/**";
        ZipalignTool zipalign = new ZipalignTool(env, workspace, listener.getLogger(), androidHome, zipalignPath);
        Map<String,String> apksToArchive = new LinkedHashMap<>();

        StandardCertificateCredentials keyStoreCredential = getKeystore(getKeyStoreId(), run.getParent());
        char[] storePassword = keyStoreCredential.getPassword().getPlainText().toCharArray();
        // TODO: add key password support
        char[] keyPassword = storePassword;
        KeyStore keyStore = keyStoreCredential.getKeyStore();
        String alias = getKeyAlias();
        PrivateKey key;
        Certificate[] certChain;
        try {
            if (getKeyAlias() == null) {
                // TODO: search all entries to find key, throw error if multiple keys
            }
            key = (PrivateKey)keyStore.getKey(alias, keyPassword);
            certChain = keyStore.getCertificateChain(alias);
        }
        catch (GeneralSecurityException e) {
            PrintWriter details = listener.fatalError("Error reading keystore " + getKeyStoreId());
            e.printStackTrace(details);
            throw new AbortException("Error reading keystore " + getKeyStoreId());
        }

        if (key == null || certChain == null) {
            throw new AbortException("Alias " + alias +
                " does not exist or does not point to a key and certificate in certificate credentials " + getKeyStoreId());
        }

        String v1SigName = alias;
        if (v1SigName == null) {
            v1SigName = keyStoreCredential.getId();
        }

        Set<FilePath> matchedApks = new TreeSet<>(Comparator.comparing(FilePath::getRemote));
        String[] globs = getSelectionGlobs();
        for (String glob : globs) {
            FilePath[] globMatch = workspace.list(glob, excludeBuilderDir);
            if (globMatch.length == 0) {
                throw new AbortException("No APKs in workspace matching " + glob);
            }
            matchedApks.addAll(Arrays.asList(globMatch));
        }

        for (FilePath unsignedApk : matchedApks) {
            unsignedApk = unsignedApk.absolutize();
            FilePath archiveDir = builderDir.child(unsignedApk.getName());
            if (archiveDir.isDirectory()) {
                archiveDir.deleteContents();
            }
            else {
                archiveDir.mkdirs();
            }
            String archiveDirRelName = relativeToWorkspace(workspace, archiveDir);
            String unsignedPathName = unsignedApk.getRemote();
            Pattern stripUnsignedPattern = Pattern.compile("(-?unsigned)?.apk$", Pattern.CASE_INSENSITIVE);
            Matcher stripUnsigned = stripUnsignedPattern.matcher(unsignedApk.getName());
            String strippedApkName = stripUnsigned.replaceFirst("");
            String alignedRelName = archiveDirRelName + "/" + strippedApkName + "-aligned.apk";
            String signedRelName = archiveDirRelName + "/" + strippedApkName + "-signed.apk";

            ArgumentListBuilder zipalignCommand = zipalign.commandFor(unsignedPathName, alignedRelName);
            listener.getLogger().printf("[SignApksBuilder] %s%n", zipalignCommand);
            int zipalignResult = launcher.launch()
                .cmds(zipalignCommand)
                .pwd(workspace)
                .stdout(listener)
                .stderr(listener.getLogger())
                .join();

            if (zipalignResult != 0) {
                listener.fatalError("[SignApksBuilder] zipalign failed: exit code %d", zipalignResult);
                throw new AbortException(String.format("zipalign failed on APK %s: exit code %d", unsignedPathName, zipalignResult));
            }

            FilePath alignedPath = workspace.child(alignedRelName);
            if (!alignedPath.exists()) {
                throw new AbortException(String.format("aligned APK does not exist: %s", alignedRelName));
            }

            listener.getLogger().printf("[SignApksBuilder] signing APK %s%n", alignedRelName);

            FilePath signedPath = workspace.child(signedRelName);
            final SignApkCallable signApk = new SignApkCallable(key, certChain, v1SigName, signedPath.getRemote(), listener);
            alignedPath.act(signApk);

            listener.getLogger().printf("[SignApksBuilder] signed APK %s%n", signedRelName);

            if (getArchiveUnsignedApks()) {
                listener.getLogger().printf("[SignApksBuilder] archiving unsigned APK %s%n", unsignedPathName);
                apksToArchive.put(archiveDirRelName + "/" + unsignedApk.getName(), relativeToWorkspace(workspace, unsignedApk));
            }
            if (getArchiveSignedApks()) {
                listener.getLogger().printf("[SignApksBuilder] archiving signed APK %s%n", signedRelName);
                apksToArchive.put(signedRelName, signedRelName);
            }
        }

        listener.getLogger().println("[SignApksBuilder] finished signing APKs");

        if (apksToArchive.size() > 0) {
            run.pickArtifactManager().archive(workspace, launcher, BuildListenerAdapter.wrap(listener), apksToArchive);
        }
    }

    private String relativeToWorkspace(FilePath ws, FilePath path) throws IOException, InterruptedException {
        URI relUri = ws.toURI().relativize(path.toURI());
        return relUri.getPath().replaceFirst("/$", "");
    }

    private StandardCertificateCredentials getKeystore(String keyStoreName, Item item) {
        List<StandardCertificateCredentials> creds = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, item, ACL.SYSTEM, NO_REQUIREMENTS);
        return CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(keyStoreName));
    }

    @Extension
    @Symbol("signAndroidApks")
    public static final class SignApksDescriptor extends BuildStepDescriptor<Builder> {

        static final String DISPLAY_NAME = Messages.builderDisplayName();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public SignApksDescriptor() {
            super();
            load();
        }

        @Override
        public @Nonnull String getDisplayName() {
            return DISPLAY_NAME;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillKeyStoreIdItems(@AncestorInPath ItemGroup<?> parent) {
            if (parent == null) {
                parent = Jenkins.getInstance();
            }
            ListBoxModel items = new ListBoxModel();
            List<StandardCertificateCredentials> keys = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, parent, ACL.SYSTEM, SignApksBuilder.NO_REQUIREMENTS);
            for (StandardCertificateCredentials key : keys) {
                items.add(key.getDescription(), key.getId());
            }
            return items;
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckAlias(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return FormValidation.validateRequired(value);
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckApksToSign(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, InterruptedException {
            if (project == null) {
                return FormValidation.warning(Messages.validation_noProject());
            }
            FilePath someWorkspace = project.getSomeWorkspace();
            if (someWorkspace != null) {
                String msg = someWorkspace.validateAntFileMask(value, FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
                if (msg != null) {
                    return FormValidation.error(msg);
                }
                return FormValidation.ok();
            }
            else {
                return FormValidation.warning(Messages.validation_noWorkspace());
            }
        }

    }

    static class SignApkCallable extends MasterToSlaveFileCallable<Void> {

        private static final long serialVersionUID = 1;

        private final PrivateKey key;
        private final Certificate[] certChain;
        private final String v1SigName;
        private final String outputApk;
        private final TaskListener listener;

        SignApkCallable(PrivateKey key, Certificate[] certChain, String v1SigName, String outputApk, TaskListener listener) {
            this.key = key;
            this.certChain = certChain;
            this.v1SigName = v1SigName;
            this.outputApk = outputApk;
            this.listener = listener;
        }

        @Override
        public Void invoke(File inputApkFile, VirtualChannel channel) throws IOException, InterruptedException {

            File outputApkFile = new File(outputApk);
            if (outputApkFile.isFile()) {
                listener.getLogger().printf("[SignApksBuilder] deleting previous signed APK %s%n", outputApk);
                if (!outputApkFile.delete()) {
                    throw new AbortException("failed to delete previous signed APK " + outputApk);
                }
            }

            List<X509Certificate> certs = new ArrayList<>(certChain.length);
            for (Certificate cert : certChain) {
                certs.add((X509Certificate) cert);
            }
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(v1SigName, key, certs).build();
            List<ApkSigner.SignerConfig> signerConfigs = Collections.singletonList(signerConfig);

            ApkSigner.Builder signerBuilder = new ApkSigner.Builder(signerConfigs)
                .setInputApk(inputApkFile)
                .setOutputApk(outputApkFile)
                .setOtherSignersSignaturesPreserved(false)
                // TODO: add to jenkins descriptor
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true);

            ApkSigner signer = signerBuilder.build();
            try {
                signer.sign();
            }
            catch (Exception e) {
                PrintWriter details = listener.fatalError("[SignApksBuilder] error signing APK %s", inputApkFile.getAbsolutePath());
                e.printStackTrace(details);
                throw new AbortException("failed to sign APK " + inputApkFile.getAbsolutePath() + ": " + e.getLocalizedMessage());
            }

            return null;
        }
    }

}
