Jenkins Android Signing Plugin
============

This Jenkins plugin provides a simple build step for [signing Android APK](https://developer.android.com/studio/publish/app-signing.html#signing-manually)
build artifacts.

This version is a fork from Big Nerd Ranch's now deprecated
[original repository](https://github.com/bignerdranch/jenkins-android-signing)
which is the basis of a nice blog post,
[Continuous Delivery for Android](https://www.bignerdranch.com/blog/continuous-delivery-for-android/).

This plugin depends on the
[Jenkins Credentials Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
for retrieving private key credentials for signing APKs.  This plugin also
depends on Android's [`apksig`](https://android.googlesource.com/platform/tools/apksig/)
library to sign APKs programmatically. `apksig` backs the [`apksigner`](https://developer.android.com/studio/command-line/apksigner.html)
utility in the Android SDK Developer Tools package.  Thanks to Google/Android for making
that library available as a
[Maven dependency](http://jcenter.bintray.com/com/android/tools/build/apksig/).
Using `apksig` ensures the signed APKs this plugin produces comply with the newer
[APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html).


Currently, one must set the environment variable `ANDROID_ZIPALIGN` in
Jenkins to the path of the
[`zipalign`](https://developer.android.com/studio/command-line/zipalign.html)
binary provided with the Android Build Tools SDK package, .e.g, `${ANDROID_HOME}/build-tools/25.0.2/zipalign`.
This therefore implies that whatever Jenkins node is performing the build has
access to an installed Android SDK.

