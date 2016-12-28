Jenkins Android Signing Plugin
============

The Android Signing plugin provides a simple build step for [signing Android APK](https://developer.android.com/studio/publish/app-signing.html#signing-manually)
build artifacts.  The advantage of this plugin is that you can use Jenkins to
centrally manage, protect, and provide all of your Android release signing
certificates without the need to distribute private keys and passwords to
every developer.  This is especially useful in multi-node/cloud environments
so you do not need to copy the signing keystore to every Jenkins node. This
plugin also does not use a shell command to perform the signing, eliminating
the potential that private key passwords appear on a command-line invocation.

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
[Maven dependency](http://jcenter.bintray.com/com/android/tools/build/apksig/).

## Building

Run `mvn package` to build a deployable HPI bundle for Jenkins.

## Installation

First, make sure your Jenkins instance has the Credentials Plugin.  Check the
[POM](pom.xml) for version requirements.  Copy the `target/android-signing.hpi`
plugin bundle to `$JENKINS_HOME/plugins/` directory, and restart Jenkins.

As of this writing, this plugin is not yet hosted in the Jenkins Update Centre.

## Usage

Before adding a _Sign APKs_ build step to a job, you must configure a certificate
credential using the Credentials Plugin's UI.  As of this wrtiting, this plugin
requires a password-protected PKCS12 keystore containing a private key entry
protected by the same password.  This will probably change in a future version to
allow password-free keys or keys with separate passwords.

As of this writing, you must set the environment variable `ANDROID_ZIPALIGN` in
Jenkins to the path of the
[`zipalign`](https://developer.android.com/studio/command-line/zipalign.html)
executable provided with the Android Build Tools SDK package, .e.g,
`${ANDROID_HOME}/build-tools/25.0.2/zipalign`.  This therefore implies that
whatever Jenkins node is performing the build has access to an installed Android
SDK, which is likely the case if you built your APK in a Jenkins job as well.

Once the prerequisites are setup, you can now add the _Sign APKs_ build step to
a job.  The configuration UI is fairly straight forward.  Select the certificate
credential you created previously, supply the alias of the private key and
certificate chain, and finally supply the name or glob pattern specifying the
APK files relative to the workspace you want to sign.

## License and Copyright

See the LICENSE file and NOTICE text file for original Work and Derivative Work copyright and license information.
