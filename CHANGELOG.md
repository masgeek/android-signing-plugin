# Jenkins Android Signing Plugin
# Version History

## 2.2.2 - 19 May 2017
Minor bug fix ([JENKINS-44299](https://issues.jenkins-ci.org/browse/JENKINS-44299))
* The _Sign Android APKs_ build step form validation now catches the `InterruptedException` that occurs in the above issue,
  as well as validates all globs in a comma-separated multi-value of the _APKs to Sign_ form field. 

## 2.2.1 - 10 April 2016
* Omit the `-signed` component of the output signed APKs for the _Output to unsigned APK sibling_/`UnsignedApkSiblingMapping` 
  option when the unsigned APK includes the `-unsigned` component.  This is to align with the intention of the change in the 
  previous release to write signed APKs the same way the Android Gradle plugin does.  Apologies for the excessive changes.

## 2.2.0 - 3 April 2016
* Skip zipalign option - This is primarily to support signing debug APKs, for which the zipalign command fails.
  Choose this option in the advanced section of the config UI
* Signed APK output option - Signed APKs are now written to the directory of the input unsigned APK by default.  Change this option in the advanced section of 
  the config UI.  
  * Previously saved _Sign APKs_ steps of Freestyle jobs will output signed APKs as before to the
  `SignApksBuilder-out` directory.
  * Pipeline scripts with `signAndroidApks` steps will write signed APKs in the new default manner.
  You can specify the old behavior like the this: 
  ```groovy
  signAndroidApks (
     // ...
     signedApkMapping: [$class: 'UnsignedApkBuilderDirMapping']
  )
  ```
  * _Sign APKs_ steps previously or newly generated from a _Job DSL_ plugin script will write signed APKs in the new default manner.  You can specify the old
  behavior like this:
  ```groovy
  job('myAndroidApp.seed') {
    steps {
      signAndroidApks '**/*-unsigned.apk', {
        signedApkMapping { unsignedApkNameDir() }
      }
  }
  ```
* Updated Pipeline/Workflow dependency to 2.0
* Updated Android apksig dependency to 2.3.0 release

## 2.1.0 - 1 Mar 2017
This release includes some significant changes:
* Config format change that is not backwards compatible
  * The Sign APKs build step no longer accepts multiple APK signing entries.  You must instead use multiple Sign APKs build steps.  However, in most cases, if you are actually signing multiple APKs, the comma-separated globs in the APKs to Sign field should cover your needs with just one build step.
  * When you install the new version of the plugin and restart Jenkins, the plugin will automatically upgrade any of your jobs that include Sign APKs build steps.
  * Be aware that you **cannot downgrade** the plugin after upgrading and expect your Sign APKs build step configurations to remain intact.
* Jenkins [Pipeline](https://jenkins.io/doc/book/pipeline/) support - see the [README](README.md)
* [Job DSL](https://github.com/jenkinsci/job-dsl-plugin/wiki) support - see the [README](README.md)
* As part of the the Pipeline support, the build step configuration form now offers parameters to override the `ANDROID_HOME` location or the Zipalign Path (`ANDROID_ZIPALIGN` environment variable) directly. Access these fields by clicking the _Advanced_ button at the top of the _Sign APKs_ form group. _Zipalign Path_ takes precedence over `ANDROID_HOME`. If you don't supply either of these configuration parameters in the form, the plugin will still attempt to find them from the Jenkins system environment variables.
* Build step form fields help on the configuration page
* The _Archive Signed APKs_ option now defaults to checked/true
* Improved naming of output APKs
  * The plugin will produce signed APKs relative to the job workspace at `SignApksBuilder-out/myApp-unsigned.apk/myApp-signed.apk` in case you have downstream build steps that will manipulate the signed APK.
  * The plugin no longer assumes the input, unsigned APKs have a `-unsigned` component in the file name.  If `-unsigned` is present, the plugin will replace it with `-signed`.  Otherwise, the plugin will simply insert `-signed` before the `.apk` suffix.
  
## 2.0.8 - 7 Feb 2017
Minor bug fixes
* Try using `zipalign.exe` if `zipalign` does not exist in `ANDROID_HOME` for Windows case
* Expand the zipalign path with EnvVars

## 2.0.7 - 18 Jan 2017
This is the first release since the [original plugin](https://github.com/bignerdranch/jenkins-android-signing) became unmaintained two years ago.
* Updated to use [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
* Choices to archive the unsigned and signed APKs
* Auto-discover the [zipalign command](https://developer.android.com/studio/command-line/zipalign.html) from `ANDROID_HOME`, or override with `ANDROID_ZIPALIGN`
* [Folder-scope-aware](https://wiki.jenkins-ci.org/display/JENKINS/CloudBees+Folders+Plugin) credentials lookup