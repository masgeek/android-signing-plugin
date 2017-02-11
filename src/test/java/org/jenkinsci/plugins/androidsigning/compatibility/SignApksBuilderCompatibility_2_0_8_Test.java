package org.jenkinsci.plugins.androidsigning.compatibility;

import org.jenkinsci.plugins.androidsigning.Apk;
import org.jenkinsci.plugins.androidsigning.SignApksBuilder;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import hudson.XmlFile;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class SignApksBuilderCompatibility_2_0_8_Test {

    @Test
    public void compatibleWith_2_0_8_config() throws URISyntaxException, IOException {
        URL configUrl = getClass().getResource(getClass().getSimpleName() + ".xml");
        XmlFile config = new XmlFile(new File(configUrl.toURI()));
        SignApksBuilder builder = (SignApksBuilder) config.read();

        assertThat(builder.getEntries().size(), equalTo(3));

        Apk entry = builder.getEntries().get(0);
        assertThat(entry.getKeyStore(), equalTo("android-signing-1"));
        assertThat(entry.getAlias(), equalTo("key1"));
        assertThat(entry.getSelection(), equalTo("build/outputs/apk/*-unsigned.apk"));
        assertThat(entry.getArchiveUnsignedApks(), is(true));
        assertThat(entry.getArchiveSignedApks(), is(true));

        entry = builder.getEntries().get(1);
        assertThat(entry.getKeyStore(), equalTo("android-signing-1"));
        assertThat(entry.getAlias(), equalTo("key2"));
        assertThat(entry.getSelection(), equalTo("SignApksBuilderTest.apk, SignApksBuilderTest-choc*.apk"));
        assertThat(entry.getArchiveUnsignedApks(), is(false));
        assertThat(entry.getArchiveSignedApks(), is(true));

        entry = builder.getEntries().get(2);
        assertThat(entry.getKeyStore(), equalTo("android-signing-2"));
        assertThat(entry.getAlias(), equalTo("key1"));
        assertThat(entry.getSelection(), equalTo("**/*.apk"));
        assertThat(entry.getArchiveUnsignedApks(), is(false));
        assertThat(entry.getArchiveSignedApks(), is(false));
    }

}
