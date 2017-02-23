Jenkins Android Signing Plugin
============

## Summary and Purpose

The Android Signing plugin provides a simple build step for 
[signing Android APK](https://developer.android.com/studio/publish/app-signing.html#signing-manually)
build artifacts.  The advantage of this plugin is that you can use Jenkins to
centrally manage, protect, and provide all of your Android release signing
certificates without the need to distribute private keys and passwords to
every developer.  This is especially useful in multi-node/cloud environments
so you do not need to copy the signing keystore to every Jenkins node.
Furthermore, using this plugin externalizes your keystore and private key 
passwords from your build script, and keeps them encrypted rather than storing
them in a plain-text properties file.  This plugin also does not use a shell 
command to perform the signing, eliminating the potential that private key 
passwords appear on a command-line invocation.

## Background

This version is a fork from Big Nerd Ranch's now deprecated
[original repository](https://github.com/bignerdranch/jenkins-android-signing)
which is the basis of a nice blog post,
[Continuous Delivery for Android](https://www.bignerdranch.com/blog/continuous-delivery-for-android/).
Thanks to Big Nerd Ranch for the original work.

This plugin depends on the
[Jenkins Credentials Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
for retrieving private key credentials for signing APKs.  Thanks to
[CloudBees](https://www.cloudbees.com/) and
[Stephen Connolly](https://github.com/stephenc) for the Credentials Plugin.

This plugin also depends on Android's  [`apksig`](https://android.googlesource.com/platform/tools/apksig/)
library to sign APKs programmatically. `apksig` backs the [`apksigner`](https://developer.android.com/studio/command-line/apksigner.html)
utility in the Android SDK Developer Tools package.  Using `apksig` ensures the signed APKs
this plugin produces comply with the newer
[APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html).
Thanks to Google/Android for making that library available as a
[Maven dependency](https://bintray.com/android/android-tools/com.android.tools.build.apksig).

## Building

Run `mvn package` to build a deployable HPI bundle for Jenkins.  Note this plugin
**REQUIRES JDK 1.8** to build and run because of the dependency on the Android `apksig` library.

## Installation

First, make sure your Jenkins instance has the Credentials Plugin (linked above).
Check the [POM](pom.xml) for version requirements.  Copy the `target/android-signing.hpi`
plugin bundle to `$JENKINS_HOME/plugins/` directory, and restart Jenkins.

As of this writing, this plugin is not yet hosted in the Jenkins Update Centre, so you
cannot install using the Jenkins UI.

## Usage

Before adding a _Sign APKs_ build step to a job, you must configure a certificate
credential using the Credentials Plugin's UI.  As of this writing, this plugin
requires a password-protected PKCS12 keystore containing a single private key entry
protected by the same password.  This will probably change in a future version to
allow password-free keys or keys with separate passwords.

This plugin will attempt to find the Android SDK's 
[`zipalign`](https://developer.android.com/studio/command-line/zipalign.html)
by way of the `ANDROID_HOME` environment variable.  It searches for the latest
version of the `build-tools` package installed in your SDK.  Alternatively, 
you can set the overriding `ANDROID_ZIPALIGN` environment variable to the path
of the `zipalign` executable you prefer, e.g., 
`${ANDROID_HOME}/build-tools/25.0.2/zipalign` (don't forget `.exe` for Windows).
This therefore implies that whatever Jenkins node is performing the build has 
access to an installed Android SDK, which is likely the case if you built your 
APK in a Jenkins job as well.

Once the prerequisites are setup, you can now add the _Sign APKs_ build step to
a job.  The configuration UI is fairly straight forward.  Select the certificate
credential you created previously, supply the alias of the private key/certificate
chain, and finally supply the name or [Ant-style glob](https://ant.apache.org/manual/dirtasks.html)
pattern specifying the APK files relative to the job workspace you want to sign.
You can specify multiple glob patterns separated by commas if you wish.  For most
projects `**/*-unsigned.apk` should suffice.

Note that this plugin assumes your Android build has produced an unsigned, 
unaligned APK.  If you are using the Gradle Android plugin to build your APK, 
that means a previous Jenkins build step probably invoked the `assembleRelease` 
task on your build script and there were no `signingConfig` blocks that applied 
to your APK.  In that case Gradle will have produced the necessary unsigned, 
unaligned APK, ready for the Android Signing Plugin to sign.  Your unsigned 
APK will then likely have the standard `-unsigned.apk` suffix, in which case the
plugin will replace the `-unsigned` component with `-signed` on the output APK.
Otherwise, the plugin will just insert `-signed` before `.apk` in the unsigned 
APK name.

Currently, all signed APKs for a single _Sign APKs_ build step go in a workspace
directory named like `SignApksBuilder-out/my-app-unsigned.apk/my-app-signed.apk`,
where `my-app-unsigned.apk` is a directory named after the unsigned input APK.
This is to avoid multiple signing steps in a single job overwriting each other's 
output APKs, and multiple APKs matched within a signing step colliding.  It's 
clearly not fool-proof, however, so be mindful if you are signing multple APKs
in a single job and/or signing step.  If you are using the plugin's 
_Archive Signed APKs_ and/or _Archive Unsigned APKs_ option, the plugin 
places the appropirate artifacts in under the `SignApksBuilder-out/my-app-unsigned.apk/`
directory in the build's archive.

### Pipeline

Here is an example of signing APKs from a [Pipeline](https://jenkins.io/doc/book/pipeline/) script:
```
node {
    // ... steps to build unsigned APK ...
    signAndroidApks (
      keyStoreId: "myApp.signerKeyStore", keyAlias: "myTeam",
      apksToSign: "**/*-unsigned.apk"
      // you can override these within the script if necessary
      // androidHome: env.ANDROID_HOME
      // zipalignPath: env.ANDROID_ZIPALIGN
    )
}
```
Like the Free Style Job build step described above, the Pipeline step will attempt
to use `ANDROID_ZIPALIGN` and `ANDROID_HOME`, in that priority order, from the
Jenkins environment variables.  Note the wrapping 
[`node`](https://jenkins.io/doc/pipeline/steps/workflow-durable-task-step/#node-allocate-node)
context; this plugin assumes the Pipeline step will have a workspace available.

## Support

Please submit all issues to [Jenkins Jira](https://issues.jenkins-ci.org/issues/?jql=project%3DJENKINS%20AND%20component%3Dandroid-signing-plugin).
Do not use GitHub issues.

## Release Notes

See the [Android Signing](https://plugins.jenkins.io/android-signing) page on the Jenkins.io site.

## License and Copyright

See the included LICENSE and NOTICE text files for original Work and Derivative
Work copyright and license information.
