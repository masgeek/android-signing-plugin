package org.jenkinsci.plugins.androidsigning

import hudson.model.FreeStyleProject
import javaposse.jobdsl.plugin.ExecuteDslScripts
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.instanceOf
import static org.hamcrest.CoreMatchers.nullValue
import static org.junit.Assert.*

class SignApksDslContextTest {

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    @Test
    void parsesSignApksDsl() {
        ExecuteDslScripts dslScripts = new ExecuteDslScripts(
            """
            job('${this.class.simpleName}-generated') {
                steps {
                
                    signAndroidApks '**/*-unsigned.apk', {
                        keyStoreId 'my.keyStore'
                        keyAlias 'myKey'
                        archiveSignedApks true
                        archiveUnsignedApks true
                        androidHome '/fake/android-sdk'
                        skipZipalign true
                    }
                    
                    signAndroidApks '**/*-other.apk', {
                        keyStoreId 'my.otherKeyStore'
                        keyAlias 'myOtherKey'
                        archiveSignedApks false
                        archiveUnsignedApks false
                        zipalignPath '/fake/android-sdk/zipalign'
                        signedApkMapping unsignedApkNameDir()
                    }
                }
            }
            """)
        FreeStyleProject job = testJenkins.createFreeStyleProject("${this.class.simpleName}-seed")
        job.buildersList.add(dslScripts)
        testJenkins.buildAndAssertSuccess(job)
        job = testJenkins.jenkins.getItemByFullName("${this.class.simpleName}-generated", FreeStyleProject)

        assertThat(job.builders.size(), equalTo(2))

        SignApksBuilder signApks = job.builders[0]

        assertThat(signApks.apksToSign, equalTo("**/*-unsigned.apk"))
        assertThat(signApks.keyStoreId, equalTo("my.keyStore"))
        assertThat(signApks.keyAlias, equalTo("myKey"))
        assertTrue(signApks.skipZipalign)
        assertTrue(signApks.archiveSignedApks)
        assertTrue(signApks.archiveUnsignedApks)
        assertThat(signApks.androidHome, equalTo("/fake/android-sdk"))
        assertThat(signApks.zipalignPath, nullValue())
        assertThat(signApks.signedApkMapping, instanceOf(SignedApkMappingStrategy.UnsignedApkSiblingMapping))

        signApks = job.builders[1]

        assertThat(signApks.apksToSign, equalTo("**/*-other.apk"))
        assertThat(signApks.keyStoreId, equalTo("my.otherKeyStore"))
        assertThat(signApks.keyAlias, equalTo("myOtherKey"))
        assertFalse(signApks.skipZipalign)
        assertFalse(signApks.archiveSignedApks)
        assertFalse(signApks.archiveUnsignedApks)
        assertThat(signApks.androidHome, nullValue())
        assertThat(signApks.zipalignPath, equalTo("/fake/android-sdk/zipalign"))
        assertThat(signApks.signedApkMapping, instanceOf(org.jenkinsci.plugins.androidsigning.SignedApkMappingStrategy.UnsignedApkBuilderDirMapping.class))
    }
}
