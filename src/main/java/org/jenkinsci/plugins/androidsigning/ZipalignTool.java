package org.jenkinsci.plugins.androidsigning;

import org.apache.commons.lang.StringUtils;

import java.io.PrintStream;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.ArgumentListBuilder;
import hudson.util.VersionNumber;


class ZipalignTool {

    private static FilePath findFromEnv(EnvVars env, FilePath workspace, PrintStream logger) throws AbortException {

        String zipalignPath = env.get("ANDROID_ZIPALIGN");
        if (!StringUtils.isEmpty(zipalignPath)) {
            zipalignPath = env.expand(zipalignPath);
            logger.printf("[SignApksBuilder] found zipalign path in env ANDROID_ZIPALIGN=%s%n", zipalignPath);
            return new FilePath(workspace.getChannel(), zipalignPath);
        }

        String androidHome = env.get("ANDROID_HOME");
        if (StringUtils.isEmpty(androidHome)) {
            throw new AbortException("failed to find zipalign: no environment variable ANDROID_ZIPALIGN or ANDROID_HOME");
        }

        androidHome = env.expand(androidHome);
        FilePath buildTools = new FilePath(workspace.getChannel(), androidHome).child("build-tools");
        List<FilePath> versionDirs;
        try {
            versionDirs = buildTools.listDirectories();
        }
        catch (Exception e) {
            e.printStackTrace(logger);
            throw new AbortException(String.format(
                "failed to find zipalign: error listing build-tools versions in %s: %s",
                buildTools.getRemote(), e.getLocalizedMessage()));
        }

        if (versionDirs == null || versionDirs.isEmpty()) {
            throw new AbortException("failed to find zipalign: no build-tools directory in ANDROID_HOME path " + androidHome);
        }

        SortedMap<VersionNumber, FilePath> versions = new TreeMap<>();
        for (FilePath versionDir : versionDirs) {
            String versionName = versionDir.getName();
            VersionNumber version = new VersionNumber(versionName);
            versions.put(version, versionDir);
        }

        if (versions.isEmpty()) {
            throw new AbortException(
                "failed to find zipalign: no build-tools versions in ANDROID_HOME path " + buildTools);
        }

        VersionNumber latest = versions.lastKey();
        FilePath zipalign = versions.get(latest);
        zipalign = zipalign.child("zipalign");

        try {
            if (!zipalign.exists()) {
                throw new AbortException("failed to find zipalign: zipalign does not exist in latest build-tools path " +
                    zipalign.getParent().getRemote());
            }
        }
        catch (Exception e) {
            e.printStackTrace(logger);
            throw new AbortException(
                String.format("failed to find zipalign: error listing build-tools versions in %s: %s",
                    buildTools.getRemote(), e.getLocalizedMessage()));
        }

        logger.printf("[SignApksBuilder] found zipalign in Android SDK's latest build tools: %s%n", zipalign.getRemote());
        return zipalign;
    }

    private final EnvVars env;
    private final FilePath workspace;
    private final PrintStream logger;
    private FilePath zipalign;

    ZipalignTool(@Nonnull EnvVars env, @Nonnull FilePath workspace, @Nonnull PrintStream logger, @Nullable String overrideZipalignPath) {
        this.env = env;
        this.workspace = workspace;
        this.logger = logger;

        if (StringUtils.isNotEmpty(overrideZipalignPath)) {
            overrideZipalignPath = env.expand(overrideZipalignPath);
            zipalign = new FilePath(workspace.getChannel(), overrideZipalignPath);
        }
    }

    ArgumentListBuilder commandFor(String unsignedApk, String outputApk) throws AbortException {
        if (zipalign == null) {
            zipalign = findFromEnv(env, workspace, logger);
        }

        return new ArgumentListBuilder()
            .add(zipalign.getRemote())
            .add("-f")
            .add("-p").add("4")
            .add(unsignedApk)
            .add(outputApk);
    }
}
