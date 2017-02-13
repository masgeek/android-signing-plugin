package org.jenkinsci.plugins.androidsigning.compatibility;

import org.jenkinsci.plugins.androidsigning.Apk;
import org.jenkinsci.plugins.androidsigning.SignApksBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.net.URISyntaxException;

import hudson.model.FreeStyleProject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class SignApksBuilderCompatibility_2_0_8_Test {

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    @Test
    @LocalData
    public void compatibleWith_2_0_8() throws URISyntaxException, IOException {

        FreeStyleProject job = (FreeStyleProject) testJenkins.jenkins.getItem(getClass().getSimpleName());
        SignApksBuilder builder = (SignApksBuilder) job.getBuilders().get(0);

        assertThat(builder.getEntries().size(), equalTo(3));

        Apk entry = builder.getEntries().get(0);
        assertThat(entry.getKeyStore(), equalTo("android-signing-1"));
        assertThat(entry.getAlias(), equalTo("key1"));
        assertThat(entry.getApksToSign(), equalTo("build/outputs/apk/*-unsigned.apk"));
        assertThat(entry.getArchiveUnsignedApks(), is(true));
        assertThat(entry.getArchiveSignedApks(), is(true));

        entry = builder.getEntries().get(1);
        assertThat(entry.getKeyStore(), equalTo("android-signing-1"));
        assertThat(entry.getAlias(), equalTo("key2"));
        assertThat(entry.getApksToSign(), equalTo("SignApksBuilderTest.apk, SignApksBuilderTest-choc*.apk"));
        assertThat(entry.getArchiveUnsignedApks(), is(false));
        assertThat(entry.getArchiveSignedApks(), is(true));

        entry = builder.getEntries().get(2);
        assertThat(entry.getKeyStore(), equalTo("android-signing-2"));
        assertThat(entry.getAlias(), equalTo("key1"));
        assertThat(entry.getApksToSign(), equalTo("**/*.apk"));
        assertThat(entry.getArchiveUnsignedApks(), is(false));
        assertThat(entry.getArchiveSignedApks(), is(false));
    }

}
