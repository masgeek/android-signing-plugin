package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import jenkins.util.VirtualFile;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class SignApksBuilderTest {

    private static final String KEY_STORE_ID = SignApksBuilderTest.class.getSimpleName() + ".keyStore";

    private static class CopyTestWorkspace extends SimpleBuildWrapper {

        private FilePath sourceDir;

        public CopyTestWorkspace(FilePath sourceDir) {
            this.sourceDir = sourceDir;
        }

        @Override
        public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            sourceDir.copyRecursiveTo("*/**", workspace);
        }

        @Extension
        public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override
            public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
            @Override
            public String getDisplayName() {
                return getClass().getSimpleName();
            }
        }

    }

    private static class FakeZipalign implements FakeLauncher {
        @Override
        public Proc onLaunch(Launcher.ProcStarter p) throws IOException {
            List<String> cmd = p.cmds();
            String inPath = cmd.get(cmd.size() - 2);
            String outPath = cmd.get(cmd.size() - 1);
            FilePath workspace = p.pwd();
            FilePath in = workspace.child(inPath);
            FilePath out = workspace.child(outPath);
            try {
                if (!out.getParent().isDirectory()) {
                    throw new IOException("destination directory does not exist: " + out.getParent());
                }
                in.copyTo(out);
                return new FakeLauncher.FinishedProc(0);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class BuildArtifact {
        private final FreeStyleBuild build;
        private final Run.Artifact artifact;
        private BuildArtifact(FreeStyleBuild build, Run.Artifact artifact) {
            this.build = build;
            this.artifact = artifact;
        }
    }

    private static class ApkArtifactIsSignedMatcher extends BaseMatcher<BuildArtifact> {
        private final StandardCertificateCredentials signer;
        private final X509Certificate expectedCert;
        private final StringBuilder descText = new StringBuilder();
        private ApkArtifactIsSignedMatcher(Apk signingEntry) throws KeyStoreException {
            List<StandardCertificateCredentials> result = CredentialsProvider.lookupCredentials(
                StandardCertificateCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList());
            signer = CredentialsMatchers.firstOrNull(result, CredentialsMatchers.withId(KEY_STORE_ID));
            expectedCert = (X509Certificate) signer.getKeyStore().getCertificate(signingEntry.getAlias());
        }
        @Override
        public boolean matches(Object item) {
            BuildArtifact actual = (BuildArtifact)item;
            descText.append(actual.artifact.getFileName());
            try {
                VirtualFile virtualSignedApk = actual.build.getArtifactManager().root().child(actual.artifact.relativePath);
                FilePath signedApkPath = actual.build.getWorkspace().createTempFile(actual.artifact.getFileName().replace(".apk", ""), ".apk");
                signedApkPath.copyFrom(virtualSignedApk.open());
                VerifyApkCallable.VerifyResult result = signedApkPath.act(new VerifyApkCallable(TaskListener.NULL));
                if (!result.isVerified) {
                    descText.append(" not verified;");
                }
                if (!result.isVerifiedV2Scheme) {
                    descText.append(" not verified v2;");
                }
                if (!result.isVerifiedV1Scheme) {
                    descText.append(" not verified v1;");
                }
                if (result.certs.length != 1) {
                    descText.append(" signer cert chain length should be 1, was ").append(result.certs.length);
                }
                else if (!result.certs[0].equals(expectedCert)) {
                    descText.append(" signer cert differs from expected cert");
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }

            return descText.length() == actual.artifact.getFileName().length();
        }
        @Override
        public void describeTo(Description description) {
            description.appendText(descText.toString());
        }
    }

    private static BuildArtifact buildArtifact(FreeStyleBuild build, Run.Artifact artifact) {
        return new BuildArtifact(build, artifact);
    }

    private static ApkArtifactIsSignedMatcher isSignedWith(Apk signingEntry) throws KeyStoreException {
        return new ApkArtifactIsSignedMatcher(signingEntry);
    }

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    @Rule
    public TestName currentTestName = new TestName();

    private StandardCertificateCredentials credentials = null;
    private FilePath sourceWorkspace = null;

    @Before
    public void addCredentials() {
        try {
            InputStream keyStoreIn = getClass().getResourceAsStream("/" + getClass().getSimpleName() + ".p12");
            byte[] keyStoreBytes = new byte[keyStoreIn.available()];
            keyStoreIn.read(keyStoreBytes);
            String keyStore = new String(Base64.getEncoder().encode(keyStoreBytes), "utf-8");
            credentials = new CertificateCredentialsImpl(
                CredentialsScope.GLOBAL, KEY_STORE_ID, "", getClass().getSimpleName(),
                new CertificateCredentialsImpl.UploadedKeyStoreSource(keyStore));
            CredentialsStore store = CredentialsProvider.lookupStores(testJenkins.jenkins).iterator().next();
            store.addCredentials(Domain.global(), credentials);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setupEnvironment() throws Exception {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        URL androidHomeUrl = getClass().getResource("/android");
        String androidHomePath = androidHomeUrl.getPath();
        envVars.put("ANDROID_HOME", androidHomePath);
        testJenkins.jenkins.getGlobalNodeProperties().add(prop);

        URL workspaceUrl = getClass().getResource("/workspace");
        sourceWorkspace = new FilePath(new File(workspaceUrl.toURI()));

        FakeZipalign zipalign = new FakeZipalign();
        PretendSlave slave = testJenkins.createPretendSlave(zipalign);
        slave.setLabelString(slave.getLabelString() + " " + getClass().getSimpleName());
    }

    @After
    public void removeCredentials() {
        CredentialsStore store = CredentialsProvider.lookupStores(testJenkins.jenkins).iterator().next();
        try {
            store.removeCredentials(Domain.global(), credentials);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        credentials = null;
    }

    private FreeStyleProject createSignApkJob() throws IOException {
        FreeStyleProject job = testJenkins.createFreeStyleProject(currentTestName.getMethodName());
        job.getBuildWrappersList().add(new CopyTestWorkspace(sourceWorkspace));
        job.setAssignedLabel(Label.get(getClass().getSimpleName()));
        return job;
    }

    @Test
    public void credentailsExist() {
        List<StandardCertificateCredentials> result = CredentialsProvider.lookupCredentials(
            StandardCertificateCredentials.class, testJenkins.jenkins, ACL.SYSTEM, Collections.emptyList());
        StandardCertificateCredentials credentials = CredentialsMatchers.firstOrNull(result, CredentialsMatchers.withId(KEY_STORE_ID));
        assertThat(credentials, sameInstance(this.credentials));
        try {
            assertTrue(credentials.getKeyStore().containsAlias(getClass().getSimpleName()));
        }
        catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void archivesTheSignedApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk", false, true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
    }

    @Test
    public void archivesTheUnsignedApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk", true, false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-unsigned.apk"));
    }

    @Test
    public void archivesTheUnsignedAndSignedApks() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk", true, true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(2));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        Run.Artifact unsignedApkArtifact = artifacts.get(1);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-unsigned.apk"));
    }

    @Test
    public void archivesNothing() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk", false, false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts, empty());
    }

    @Test
    public void signsTheApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk", false, true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        Run.Artifact signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSignedWith(entries.get(0)));
    }

    @Test
    public void supportsApksWithoutUnsignedSuffix() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "SignApksBuilderTest.apk", true, true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        Run.Artifact signedApkArtifact = artifacts.get(0);
        Run.Artifact unsignedApkArtifact = artifacts.get(1);

        assertThat(buildArtifact(build, signedApkArtifact), isSignedWith(entries.get(0)));
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.getFileName(), equalTo("SignApksBuilderTest.apk"));
    }

    @Test
    public void signsAllMatchingApks() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "SignApksBuilderTest-*.apk", true, true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));
        assertThat(artifacts, hasItems(
            hasProperty("fileName", endsWith("SignApksBuilderTest-chocolate_flavor.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-chocolate_flavor-signed.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-unsigned.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-signed.apk"))));

        //noinspection Duplicates
        artifacts.forEach(artifact -> {
            try {
                if (!artifact.getFileName().endsWith("-signed.apk")) {
                    return;
                }
                assertThat(buildArtifact(build, artifact), isSignedWith(entries.get(0)));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void signsMultipleApksThatWillHaveConflictingSignedFileNames() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "SignApksBuilderTest.apk, SignApksBuilderTest-unsigned.apk", true, true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));

        List<String> relPaths = artifacts.stream().map(artifact -> artifact.relativePath).collect(Collectors.toList());
        assertThat(relPaths, hasItems(
            "SignApksBuilder/" + KEY_STORE_ID + "-1/SignApksBuilderTest.apk",
            "SignApksBuilder/" + KEY_STORE_ID + "-1/SignApksBuilderTest-signed.apk",
            "SignApksBuilder/" + KEY_STORE_ID + "-2/SignApksBuilderTest-unsigned.apk",
            "SignApksBuilder/" + KEY_STORE_ID + "-2/SignApksBuilderTest-signed.apk"));

        FilePath[] workApks = build.getWorkspace().list("SignApksBuilder/" + KEY_STORE_ID + "-*/**/*.apk");
        assertThat(workApks.length, equalTo(4));

        //noinspection Duplicates
        artifacts.forEach(artifact -> {
            try {
                if (!artifact.getFileName().endsWith("-signed.apk")) {
                    return;
                }
                assertThat(buildArtifact(build, artifact), isSignedWith(entries.get(0)));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void supportsMultipleApkGlobs() throws Exception {

    }

}
