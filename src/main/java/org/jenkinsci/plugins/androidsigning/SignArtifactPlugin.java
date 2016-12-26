package org.jenkinsci.plugins.androidsigning;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkSignerEngine;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class SignArtifactPlugin extends Builder {

    private static final List<DomainRequirement> NO_REQUIREMENTS = Collections.emptyList();

    private List<Apk> entries = Collections.emptyList();

    @DataBoundConstructor
    public SignArtifactPlugin(List<Apk> apks) {
        this.entries = apks;
        if (this.entries == null) {
            this.entries = Collections.emptyList();
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private boolean isPerformDeployment(AbstractBuild build) {
        Result result = build.getResult();
        if (result == null) {
            return true;
        }

        return build.getResult().isBetterOrEqualTo(Result.UNSTABLE);
    }

    @SuppressWarnings("unused")
    public List<Apk> getEntries() {
        return entries;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (!isPerformDeployment(build)) {
            listener.getLogger().println("[AndroidSignPlugin] - Skipping signing APKs ...");
        }

        for (Apk entry : entries) {
            StringTokenizer rpmGlobTokenizer = new StringTokenizer(entry.getSelection(), ",");
            StandardCertificateCredentials keystore = getKeystore(entry.getKeyStore(), build.getProject());
            listener.getLogger().println("[AndroidSignPlugin] - Signing " + rpmGlobTokenizer.countTokens() + " APKs");
            while (rpmGlobTokenizer.hasMoreTokens()) {
                String rpmGlob = rpmGlobTokenizer.nextToken();
                FilePath[] matchedApks = build.getWorkspace().list(rpmGlob);
                if (ArrayUtils.isEmpty(matchedApks)) {
                    listener.getLogger().println("[AndroidSignPlugin] - No APKs matching " + rpmGlob);
                }
                else {
                    for (FilePath rpmFilePath : matchedApks) {

                        ArgumentListBuilder apkSignCommand = new ArgumentListBuilder();
                        String cleanPath = rpmFilePath.toURI().normalize().getPath();
                        String signedPath = cleanPath.replace("unsigned", "signed");
                        String alignedPath = signedPath.replace("signed", "signed-aligned");

                        StandardCertificateCredentials keyStoreCredential = getKeystore(entry.getKeyStore(), build.getProject());
                        char[] storePassword = keyStoreCredential.getPassword().getPlainText().toCharArray();
                        // TODO: add key password support
                        char[] keyPassword = storePassword;
                        KeyStore keyStore = keyStoreCredential.getKeyStore();
                        PrivateKey key = null;
                        ArrayList<X509Certificate> certs = new ArrayList<>();
                        try {
                            key = (PrivateKey)keyStore.getKey(entry.getAlias(), keyPassword);
                            Certificate[] certChain = keyStore.getCertificateChain(entry.getAlias());
                            for (Certificate cert : certChain) {
                                certs.add((X509Certificate)cert);
                            }
                        }
                        // TODO: proper error handling in jenkins
                        catch (KeyStoreException e) {
                            e.printStackTrace();
                        }
                        catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                        catch (UnrecoverableKeyException e) {
                            e.printStackTrace();
                        }
                        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder("", key, certs).build();
                        List<ApkSigner.SignerConfig> signerConfigs = Collections.singletonList(signerConfig);
                        ApkSigner.Builder signerBuilder = new ApkSigner.Builder(signerConfigs);

//                        FilePath key = keystore.makeTempPath(build.getWorkspace());
//
//                        //jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore my-release-key.keystore my_application.apk alias_name
//                        apkSignCommand.add("jarsigner");
//                        apkSignCommand.add("-sigalg", "SHA1withRSA");
//                        apkSignCommand.add("-digestalg", "SHA1");
//                        apkSignCommand.add("-keystore", key.getRemote());
//                        apkSignCommand.add("-storepass");
//                        apkSignCommand.addMasked(keystore.getPassphrase());
//                        apkSignCommand.add("-signedjar", signedPath);
//                        apkSignCommand.add(cleanPath);
//                        apkSignCommand.add(entry.getAlias());

                        listener.getLogger().println("[AndroidSignPlugin] - Signing on " + Computer.currentComputer().getDisplayName());

                        Launcher.ProcStarter ps = launcher.new ProcStarter();
                        ps = ps.cmds(apkSignCommand).stdout(listener);
                        ps = ps.pwd(rpmFilePath.getParent()).envs(build.getEnvironment(listener));
                        Proc proc = launcher.launch(ps);

                        int retcode = proc.join();
                        if (retcode != 0) {
                            listener.getLogger().println("[AndroidSignPlugin] - Failed signing APK");
                            return false;
                        }

                        Map<String,String> artifactsInsideWorkspace = new LinkedHashMap<String,String>();
                        artifactsInsideWorkspace.put(signedPath, stripWorkspace(build.getWorkspace(), signedPath));

                        ///opt/android-sdk/build-tools/20.0.0/zipalign
                        String zipalign = build.getEnvironment(listener).get("ANDROID_ZIPALIGN");
                        if (zipalign == null || StringUtils.isEmpty(zipalign)) {
                            throw new RuntimeException("You must set the environmental variable ANDROID_ZIPALIGN to point to the correct binary");
                        }
                        ArgumentListBuilder zipalignCommand = new ArgumentListBuilder();
                        zipalignCommand.add(zipalign);
                        zipalignCommand.add("4");
                        zipalignCommand.add(signedPath);
                        zipalignCommand.add(alignedPath);

                        Launcher.ProcStarter ps2 = launcher.new ProcStarter();
                        ps2 = ps2.cmds(zipalignCommand).stdout(listener);
                        ps2 = ps2.pwd(rpmFilePath.getParent()).envs(build.getEnvironment(listener));
                        Proc proc2 = launcher.launch(ps2);
                        retcode = proc2.join();
                        if(retcode != 0) {
                            listener.getLogger().println("[AndroidSignPlugin] - Failed aligning APK");
                            return true;
                        }
                        artifactsInsideWorkspace.put(alignedPath, stripWorkspace(build.getWorkspace(), alignedPath));
                        build.pickArtifactManager().archive(build.getWorkspace(), launcher, listener, artifactsInsideWorkspace);
                    }

                }
            }
        }

        listener.getLogger().println("[AndroidSignPlugin] - Finished signing APKs ...");

        return true;
    }

    private void signApks(FilePath[] matchedApks, StandardCertificateCredentials keystore) {

    }

    private String stripWorkspace(FilePath ws, String path) {
        return path.replace(ws.getRemote(), "");
    }

    private StandardCertificateCredentials getKeystore(String keyStoreName, Item item) {
        List<StandardCertificateCredentials> creds = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, item, ACL.SYSTEM, NO_REQUIREMENTS);
        return CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(keyStoreName));
    }

    @Extension
    @SuppressWarnings("unused")
    public static final class SignArtifactDescriptor extends BuildStepDescriptor<Builder> {

        public static final String DISPLAY_NAME = Messages.job_displayName();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public SignArtifactDescriptor() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        public ListBoxModel doFillKeystoreItems() {
            ListBoxModel items = new ListBoxModel();
            List<StandardCertificateCredentials> keys = CredentialsProvider.lookupCredentials(
                    StandardCertificateCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, NO_REQUIREMENTS);
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
