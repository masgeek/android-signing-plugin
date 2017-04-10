package org.jenkinsci.plugins.androidsigning

import hudson.FilePath
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat


class UnsignedApkSiblingMappingTest {

    FilePath workspace = new FilePath(null, "/jenkins/jobs/UnsignedApkMappingTest/workspace")

    @Test
    void doesNotAddSignedSuffixWhenInputHasUnsignedSuffix() {
        SignedApkMappingStrategy.UnsignedApkSiblingMapping mapping = new SignedApkMappingStrategy.UnsignedApkSiblingMapping()
        FilePath inApk = workspace.child("app/build/outputs/app-unsigned.apk")
        FilePath outApk = mapping.destinationForUnsignedApk(inApk, workspace)

        assertThat(outApk, equalTo(workspace.child("app/build/outputs/app.apk")))
    }

    @Test
    void addsSignedSuffixWhenInputApkDoesNotHaveUnsignedSuffix() {
        SignedApkMappingStrategy.UnsignedApkSiblingMapping mapping = new SignedApkMappingStrategy.UnsignedApkSiblingMapping()
        FilePath inApk = workspace.child("app/build/outputs/app-other.apk")
        FilePath outApk = mapping.destinationForUnsignedApk(inApk, workspace)

        assertThat(outApk, equalTo(workspace.child("app/build/outputs/app-other-signed.apk")))
    }
}
