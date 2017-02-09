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

import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
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

    private static final List<DomainRequirement> NO_REQUIREMENTS = Collections.emptyList();

    private List<Apk> entries = Collections.emptyList();
    transient private String zipalignPath;

    @DataBoundConstructor
    public SignApksBuilder(List<Apk> apks) {
        this.entries = apks;
        if (this.entries == null) {
            this.entries = Collections.emptyList();
        }
    }

    private boolean isIntermediateFailure(Run build) {
        Result result = build.getResult();
        return result != null && result.isWorseThan(Result.UNSTABLE);
    }

    @SuppressWarnings("unused")
    public void setZipalignPath(String x) {
        this.zipalignPath = x;
    }

    @SuppressWarnings("unused")
    public List<Apk> getEntries() {
        return entries;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        if (isIntermediateFailure(run)) {
            listener.getLogger().println("[SignApksBuilder] skipping Sign APKs step because a previous step failed");
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

        ZipalignTool zipalign = new ZipalignTool(env, workspace, listener.getLogger(), zipalignPath);
        Map<String,String> apksToArchive = new LinkedHashMap<>();
        int apkCounter = 1;
        for (Apk entry : entries) {
            StandardCertificateCredentials keyStoreCredential = getKeystore(entry.getKeyStore(), run.getParent());
            char[] storePassword = keyStoreCredential.getPassword().getPlainText().toCharArray();
            // TODO: add key password support
            char[] keyPassword = storePassword;
            KeyStore keyStore = keyStoreCredential.getKeyStore();
            PrivateKey key;
            Certificate[] certChain;
            try {
                if (entry.getAlias() == null) {
                    // TODO: search all entries to find key, throw error if multiple keys
                }
                key = (PrivateKey)keyStore.getKey(entry.getAlias(), keyPassword);
                certChain = keyStore.getCertificateChain(entry.getAlias());
            }
            catch (GeneralSecurityException e) {
                PrintWriter details = listener.fatalError("Error reading keystore " + entry.getKeyStore());
                e.printStackTrace(details);
                throw new AbortException("Error reading keystore " + entry.getKeyStore());
            }

            if (key == null || certChain == null) {
                throw new AbortException("Alias " + entry.getAlias() +
                    " does not exist or does not point to a key and certificate in certificate credentials " + entry.getKeyStore());
            }

            String v1SigName = entry.getAlias();
            if (v1SigName == null) {
                v1SigName = keyStoreCredential.getId();
            }

            String safeKeyStoreId = entry.getKeyStore().replaceAll("[^\\w.-]+[^$]", "_");
            String[] globs = entry.getSelectionGlobs();
            for (String glob : globs) {
                FilePath[] matchedApks = workspace.list(glob);
                if (ArrayUtils.isEmpty(matchedApks)) {
                    throw new AbortException("No APKs in workspace matching " + glob);
                }
                for (FilePath apkPath : matchedApks) {
                    apkPath = apkPath.absolutize();
                    String archiveDirName = safeKeyStoreId + "/" + apkCounter++ + "/";

                    String unsignedPathName = apkPath.getRemote();
                    // TODO: implicit coupling to the gradle android plugin's naming convention here
                    Pattern stripUnsignedPattern = Pattern.compile("(-?unsigned)?.apk$", Pattern.CASE_INSENSITIVE);
                    Matcher stripUnsigned = stripUnsignedPattern.matcher(apkPath.getName());
                    String strippedApkPathName = stripUnsigned.replaceFirst("");
                    String alignedRelPathName = archiveDirName + strippedApkPathName + "-aligned.apk";
                    String signedRelPathName = archiveDirName + strippedApkPathName + "-signed.apk";

                    workspace.child(archiveDirName).mkdirs();

                    ArgumentListBuilder zipalignCommand = zipalign.commandFor(unsignedPathName, alignedRelPathName);
                    listener.getLogger().printf("[SignApksBuilder] %s", zipalignCommand);
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

                    FilePath alignedPath = workspace.child(alignedRelPathName);
                    if (!alignedPath.exists()) {
                        throw new AbortException(String.format("aligned APK does not exist: %s", alignedRelPathName));
                    }

                    listener.getLogger().printf("[SignApksBuilder] signing APK %s%n", alignedRelPathName);

                    FilePath signedPath = workspace.child(signedRelPathName);
                    final SignApkCallable signApk = new SignApkCallable(key, certChain, v1SigName, signedPath.getRemote(), listener);
                    alignedPath.act(signApk);

                    listener.getLogger().printf("[SignApksBuilder] signed APK %s%n", signedRelPathName);


                    if (entry.getArchiveUnsignedApks()) {
                        listener.getLogger().printf("[SignApksBuilder] archiving unsigned APK %s%n", unsignedPathName);
                        String rel = relativeToWorkspace(workspace, apkPath);
                        apksToArchive.put(archiveDirName + apkPath.getName(), rel);
                    }
                    if (entry.getArchiveSignedApks()) {
                        listener.getLogger().printf("[SignApksBuilder] archiving signed APK %s%n", signedRelPathName);
                        apksToArchive.put(signedRelPathName, signedRelPathName);
                    }
                }
            }
        }

        listener.getLogger().println("[SignApksBuilder] finished signing APKs");

        if (apksToArchive.size() > 0) {
            run.pickArtifactManager().archive(workspace, launcher, BuildListenerAdapter.wrap(listener), apksToArchive);
        }
    }

    private String relativeToWorkspace(FilePath ws, FilePath path) throws IOException, InterruptedException {
        URI relUri = ws.toURI().relativize(path.toURI());
        return relUri.getPath();
    }

    private StandardCertificateCredentials getKeystore(String keyStoreName, Item item) {
        List<StandardCertificateCredentials> creds = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, item, ACL.SYSTEM, NO_REQUIREMENTS);
        return CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(keyStoreName));
    }

    @Extension
    @SuppressWarnings("unused")
    public static final class SignApksDescriptor extends BuildStepDescriptor<Builder> {

        public static final String DISPLAY_NAME = Messages.job_displayName();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public SignApksDescriptor() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        public ListBoxModel doFillKeystoreItems(@AncestorInPath ItemGroup<?> parent) {
            if (parent == null) {
                parent = Jenkins.getInstance();
            }
            ListBoxModel items = new ListBoxModel();
            List<StandardCertificateCredentials> keys = CredentialsProvider.lookupCredentials(
                    StandardCertificateCredentials.class, parent, ACL.SYSTEM, NO_REQUIREMENTS);
            for (StandardCertificateCredentials key : keys) {
                items.add(key.getDescription(), key.getId());
            }
            return items;
        }

        public FormValidation doCheckAlias(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckSelection(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, InterruptedException {
            FilePath someWorkspace = project.getSomeWorkspace();
            if (someWorkspace != null) {
                String msg = someWorkspace.validateAntFileMask(value, FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
                if (msg != null) {
                    return FormValidation.error(msg);
                }
                return FormValidation.ok();
            }
            else {
                return FormValidation.warning(Messages.noworkspace());
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
