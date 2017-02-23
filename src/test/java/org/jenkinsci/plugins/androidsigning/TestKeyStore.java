package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;


public class TestKeyStore implements TestRule {

    public static final String KEY_STORE_ID = SignApksBuilderTest.class.getSimpleName() + ".keyStore";
    public static final String KEY_ALIAS = SignApksBuilderTest.class.getSimpleName();

    public StandardCertificateCredentials credentials;

    private JenkinsRule testJenkins;

    public TestKeyStore(JenkinsRule testJenkins) {
        this.testJenkins = testJenkins;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                addCredentials();
                try {
                    base.evaluate();
                }
                finally {
                    removeCredentials();
                }
            }
        };
    }

    private void addCredentials() {
        if (testJenkins.jenkins == null) {
            return;
        }
        try {
            InputStream keyStoreIn = SignApksBuilderTest.class.getResourceAsStream("/" + SignApksBuilderTest.class.getSimpleName() + ".p12");
            byte[] keyStoreBytes = new byte[keyStoreIn.available()];
            keyStoreIn.read(keyStoreBytes);
            String keyStore = new String(Base64.getEncoder().encode(keyStoreBytes), "utf-8");
            credentials = new CertificateCredentialsImpl(
                CredentialsScope.GLOBAL, KEY_STORE_ID, "", SignApksBuilderTest.class.getSimpleName(),
                new CertificateCredentialsImpl.UploadedKeyStoreSource(keyStore));
            CredentialsStore store = CredentialsProvider.lookupStores(testJenkins.jenkins).iterator().next();
            store.addCredentials(Domain.global(), credentials);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void removeCredentials() {
        if (testJenkins.jenkins == null) {
            return;
        }
        CredentialsStore store = CredentialsProvider.lookupStores(testJenkins.jenkins).iterator().next();
        try {
            store.removeCredentials(Domain.global(), credentials);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        credentials = null;
    }
}
