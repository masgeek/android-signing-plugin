package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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
import jenkins.tasks.SimpleBuildWrapper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.sameInstance;
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
                in.copyTo(out);
                return new FakeLauncher.FinishedProc(0);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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

    private void copyTestWorkspaceForBuild(FreeStyleBuild build) throws IOException, InterruptedException {
        sourceWorkspace.copyRecursiveTo("*/**", build.getWorkspace());
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
        assertThat(signedApkArtifact.relativePath, equalTo("SignApksBuilderTest-signed.apk"));
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
        assertThat(signedApkArtifact.relativePath, equalTo("SignApksBuilderTest-unsigned.apk"));
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
        assertThat(signedApkArtifact.relativePath, equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.relativePath, equalTo("SignApksBuilderTest-unsigned.apk"));
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
    public void supportsMultipleApkGlobs() throws Exception {

    }

}
