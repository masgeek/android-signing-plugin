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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.util.ArgumentListBuilder;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class SignApksBuilderTest {

    static final String KEY_STORE_ID = SignApksBuilderTest.class.getSimpleName() + ".keyStore";

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    StandardCertificateCredentials credentials = null;

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
    @Ignore
    public void findsZipalignInAndroidHomeEnvVar() throws Exception {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        URL androidHomeUrl = getClass().getResource("/android");
        String androidHomePath = androidHomeUrl.getPath();
        String zipalignPath = new FilePath(new File(androidHomePath, "build-tools")).child("1.0").child("zipalign").getRemote();
        envVars.put("ANDROID_HOME", androidHomePath);
        testJenkins.jenkins.getGlobalNodeProperties().add(prop);

        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk", true, true));
        SignApksBuilder builder = new SignApksBuilder(entries);

        TaskListener listener = testJenkins.createTaskListener();
        Run<?,?> run = Mockito.mock(AbstractBuild.class);
        Mockito.when(run.getEnvironment(listener)).thenReturn(envVars);
        URL workspaceUrl = getClass().getResource("/workspace");
        FilePath workspace = new FilePath(new File(workspaceUrl.toURI()));
        Launcher launcher = Mockito.mock(Launcher.class);
        Launcher.ProcStarter starter = Mockito.mock(Launcher.ProcStarter.class);
        Mockito.when(launcher.launch()).thenReturn(starter);
        Mockito.when(starter.cmds(Mockito.any(ArgumentListBuilder.class))).thenReturn(starter);
        Mockito.when(starter.pwd(Mockito.any(FilePath.class))).thenReturn(starter);
        Mockito.when(starter.stdout(listener)).thenReturn(starter);
        Mockito.when(starter.stderr(Mockito.any(OutputStream.class))).thenReturn(starter);
        Mockito.when(starter.join()).thenReturn(0);
        builder.perform(run, workspace, launcher, listener);

        ArgumentCaptor<ArgumentListBuilder> captureProcArgs = ArgumentCaptor.forClass(ArgumentListBuilder.class);
        Mockito.verify(starter).cmds(captureProcArgs.capture());

        assertThat(captureProcArgs.getValue().toString(), startsWith(zipalignPath));
    }

}
