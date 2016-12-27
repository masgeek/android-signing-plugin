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

License
-------

    Copyright 2015 Big Nerd Ranch

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
