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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;

public class SignApksBuilder extends Builder implements SimpleBuildStep {

    private static final List<DomainRequirement> NO_REQUIREMENTS = Collections.emptyList();

    private List<Apk> entries = Collections.emptyList();

    @DataBoundConstructor
    public SignApksBuilder(List<Apk> apks) {
        this.entries = apks;
        if (this.entries == null) {
            this.entries = Collections.emptyList();
        }
    }

    private boolean isIntermediateFailure(Run build) {
        Result result = build.getResult();
        return result != null && build.getResult().isWorseThan(Result.UNSTABLE);
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

        for (Apk entry : entries) {
            StringTokenizer rpmGlobTokenizer = new StringTokenizer(entry.getSelection(), ",");

            StandardCertificateCredentials keyStoreCredential = getKeystore(entry.getKeyStore(), run.getParent());
            char[] storePassword = keyStoreCredential.getPassword().getPlainText().toCharArray();
            // TODO: add key password support
            char[] keyPassword = storePassword;
            KeyStore keyStore = keyStoreCredential.getKeyStore();
            PrivateKey key;
            ArrayList<X509Certificate> certs = new ArrayList<>();
            try {
                if (entry.getAlias() == null) {
                    // TODO: search all entries to find key, throw error if multiple keys
                }
                key = (PrivateKey)keyStore.getKey(entry.getAlias(), keyPassword);
                Certificate[] certChain = keyStore.getCertificateChain(entry.getAlias());
                for (Certificate cert : certChain) {
                    certs.add((X509Certificate)cert);
                }
            }
            catch (GeneralSecurityException e) {
                PrintWriter details = listener.fatalError("Error reading keystore " + entry.getKeyStore());
                e.printStackTrace(details);
                throw new AbortException("Error reading keystore " + entry.getKeyStore());
            }

            String v1SigName = entry.getAlias();
            if (v1SigName == null) {
                v1SigName = keyStoreCredential.getId();
            }
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(v1SigName, key, certs).build();
            List<ApkSigner.SignerConfig> signerConfigs = Collections.singletonList(signerConfig);

            while (rpmGlobTokenizer.hasMoreTokens()) {
                String rpmGlob = rpmGlobTokenizer.nextToken();
                FilePath[] matchedApks = workspace.list(rpmGlob);
                if (ArrayUtils.isEmpty(matchedApks)) {
                    throw new AbortException("No APKs in workspace matching " + rpmGlob);
                }
                else {
                    for (FilePath apkPath : matchedApks) {
                        String unsignedPath = apkPath.absolutize().getRemote();
                        // TODO: implicit coupling to the gradle android plugin's naming convention here
                        String alignedPath = unsignedPath.replace("unsigned", "unsigned-aligned");
                        String signedPath = alignedPath.replace("unsigned-aligned", "signed");

                        // TODO: find zipalign myself and/or add descriptor parameter for build tools version
                        // or try to match build tools version to version for apksig library?
                        String zipalign = findZipalignPath(run.getEnvironment(listener), listener.getLogger());

                        File alignedFile = new File(alignedPath);
                        if (alignedFile.isFile()) {
                            listener.getLogger().printf("[SignApksBuilder] deleting previous aligned APK %s\n", alignedFile.getAbsolutePath());
                            alignedFile.delete();
                        }

                        ArgumentListBuilder zipalignCommand = new ArgumentListBuilder()
                            .add(zipalign)
                            .add("-v")
                            .add("-p").add("4")
                            .add(unsignedPath)
                            .add(alignedPath);

                        Launcher.ProcStarter zipalignStarter = launcher.new ProcStarter()
                            .cmds(zipalignCommand)
                            .pwd(apkPath.getParent())
                            .envs(run.getEnvironment(listener))
                            .stdout(listener);

                        Proc zipalignProc = launcher.launch(zipalignStarter);
                        int zipalignResult = zipalignProc.join();
                        if (zipalignResult != 0) {
                            listener.getLogger().println("[SignApksBuilder] failed aligning APK");
                            return;
                        }

                        File signedFile = new File(signedPath);
                        if (signedFile.isFile()) {
                            listener.getLogger().printf("[SignApksBuilder] deleting previous signed APK %s\n", signedFile.getAbsolutePath());
                            signedFile.delete();
                        }

                        listener.getLogger().printf("[SignApksBuilder] signing APK %s\n", alignedFile.getAbsolutePath());

                        ApkSigner.Builder signerBuilder = new ApkSigner.Builder(signerConfigs)
                            .setInputApk(alignedFile)
                            .setOutputApk(signedFile)
                            .setOtherSignersSignaturesPreserved(false)
                            // TODO: add to jenkins descriptor
                            .setV1SigningEnabled(true)
                            .setV2SigningEnabled(true);
                        ApkSigner signer = signerBuilder.build();
                        try {
                            signer.sign();
                        }
                        catch (Exception e) {
                            PrintWriter details = listener.fatalError("[SignApksBuilder] error signing APK %s", alignedFile.getAbsolutePath());
                            e.printStackTrace(details);
                            throw new AbortException("failed to sign APK " + alignedFile.getAbsolutePath() + ": " + e.getLocalizedMessage());
                        }

                        Map<String,String> artifactsInsideWorkspace = new LinkedHashMap<>();
                        artifactsInsideWorkspace.put(alignedPath, stripWorkspace(workspace, alignedPath));
                        artifactsInsideWorkspace.put(signedPath, stripWorkspace(workspace, signedPath));
                        run.pickArtifactManager().archive(workspace, launcher, BuildListenerAdapter.wrap(listener), artifactsInsideWorkspace);
                    }
                }
            }
        }

        listener.getLogger().println("[SignApksBuilder] finished signing APKs");
    }

    private String stripWorkspace(FilePath ws, String path) {
        return path.replace(ws.getRemote(), "");
    }

    private StandardCertificateCredentials getKeystore(String keyStoreName, Item item) {
        List<StandardCertificateCredentials> creds = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, item, ACL.SYSTEM, NO_REQUIREMENTS);
        return CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(keyStoreName));
    }

    private @Nonnull String findZipalignPath(EnvVars env, PrintStream logger) throws AbortException {
        String zipalign = env.get("ANDROID_ZIPALIGN");
        if (!StringUtils.isEmpty(zipalign)) {
            logger.printf("[SignApksBuilder] found zipalign path in env ANDROID_ZIPALIGN=%s\n", zipalign);
            return zipalign;
        }

        String androidHome = env.get("ANDROID_HOME");
        if (StringUtils.isEmpty(androidHome)) {
            throw new AbortException("failed to find zipalign: no environment variable ANDROID_ZIPALIGN or ANDROID_HOME");
        }

        File buildTools = new File(androidHome, "build-tools");
        File[] versionDirs = buildTools.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        if (versionDirs == null) {
            throw new AbortException("failed to find zipalign: no build-tools directory in ANDROID_HOME path " + buildTools);
        }

        SortedMap<VersionNumber, File> versions = new TreeMap<>();
        for (File versionDir : versionDirs) {
            if (versionDir.isDirectory()) {
                String versionName = versionDir.getName();
                VersionNumber version = new VersionNumber(versionName);
                versions.put(version, versionDir);
            }
        }

        if (versions.isEmpty()) {
            throw new AbortException(
                "failed to find zipalign: no build-tools versions in ANDROID_HOME path " + buildTools);
        }

        VersionNumber latest = versions.lastKey();
        File zipalignPath = versions.get(latest);
        zipalignPath = new File(zipalignPath, "zipalign");

        if (!zipalignPath.isFile()) {
            throw new AbortException("failed to find zipalign: zipalign does not exist in latest build-tools path " +
                zipalignPath.getParentFile().getAbsolutePath());
        }

        zipalign = zipalignPath.getAbsolutePath();
        logger.printf("[SignApksBuilder] found zipalign path in Android SDK's latest build tools %s\n", zipalign);
        return zipalign;
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

}
