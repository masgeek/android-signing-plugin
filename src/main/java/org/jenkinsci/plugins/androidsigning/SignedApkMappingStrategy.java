package org.jenkinsci.plugins.androidsigning;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.FilePath;


interface SignedApkMappingStrategy {

    FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace);

    /**
     * Return the name of the given APK without the .apk extension and without any -unsigned suffix, if present.
     * For example, {@code}myApp-unsigned.apk{@code} returns {@code}myApp{@code}, and
     * {@code}myApp-someFlavor.apk{@code} returns {@code}myApp-someFlavor{@code}.
     * @param unsignedApk
     * @return
     */
    static String unqualifiedNameOfUnsignedApk(FilePath unsignedApk) {
        Pattern stripUnsignedPattern = Pattern.compile("(-?unsigned)?$", Pattern.CASE_INSENSITIVE);
        Matcher stripUnsigned = stripUnsignedPattern.matcher(unsignedApk.getBaseName());
        return stripUnsigned.replaceFirst("");
    }

    static class UnsignedApkBuilderDirMapping implements SignedApkMappingStrategy {

        public static final UnsignedApkBuilderDirMapping INSTANCE = new UnsignedApkBuilderDirMapping();

        @Override
        public FilePath destinationForUnsignedApk(FilePath unsignedApk, FilePath workspace) {
            String strippedName = unqualifiedNameOfUnsignedApk(unsignedApk);
            return workspace.child(SignApksBuilder.BUILDER_DIR).child(unsignedApk.getName()).child(strippedName + "-signed.apk");
        }
    }


}
