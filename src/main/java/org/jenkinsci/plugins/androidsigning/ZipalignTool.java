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

    static final String ENV_ANDROID_HOME = "ANDROID_HOME";
    static final String ENV_ZIPALIGN_PATH = "ANDROID_ZIPALIGN";

    private static FilePath findFromEnv(EnvVars env, FilePath workspace, PrintStream logger) throws AbortException {

        String zipalignPath = env.get(ENV_ZIPALIGN_PATH);
        if (!StringUtils.isEmpty(zipalignPath)) {
            zipalignPath = env.expand(zipalignPath);
            logger.printf("[SignApksBuilder] found zipalign path in env %s=%s%n", ENV_ZIPALIGN_PATH, zipalignPath);
            return new FilePath(workspace.getChannel(), zipalignPath);
        }

        String androidHome = env.get(ENV_ANDROID_HOME);
        if (StringUtils.isEmpty(androidHome)) {
            throw new AbortException("failed to find zipalign: no environment variable " + ENV_ZIPALIGN_PATH + " or " + ENV_ANDROID_HOME);
        }
        androidHome = env.expand(androidHome);

        logger.printf("[SignApksBuilder] searching environment variable %s=%s for zipalign...", ENV_ANDROID_HOME, androidHome);

        return findInAndroidHome(androidHome, workspace, logger);
    }

    private static FilePath findInAndroidHome(String androidHome, FilePath workspace, PrintStream logger) throws AbortException {

        FilePath buildTools = workspace.child(androidHome).child("build-tools");
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
            throw new AbortException("failed to find zipalign: no build-tools directory in Android home path " + androidHome);
        }

        SortedMap<VersionNumber, FilePath> versions = new TreeMap<>();
        for (FilePath versionDir : versionDirs) {
            String versionName = versionDir.getName();
            VersionNumber version = new VersionNumber(versionName);
            versions.put(version, versionDir);
        }

        if (versions.isEmpty()) {
            throw new AbortException(
                "failed to find zipalign: no build-tools versions in Android home path " + buildTools);
        }

        VersionNumber latest = versions.lastKey();
        buildTools = versions.get(latest);
        FilePath zipalign = buildTools.child("zipalign");

        logger.printf("[SignApksBuilder] found zipalign in Android SDK's latest build tools: %s%n", zipalign.getRemote());

        return zipalign;
    }

    private static FilePath ensureZipalignExists(FilePath zipalign, PrintStream logger) throws AbortException {
        try {
            if (zipalign.exists()) {
                return zipalign;
            }
        }
        catch (Exception e) {
            e.printStackTrace(logger);
            throw new AbortException(e.getMessage());
        }
        try {
            zipalign = zipalign.getParent().child("zipalign.exe");
            if (zipalign.exists()) {
                return zipalign;
            }
        }
        catch (Exception e) {
            e.printStackTrace(logger);
            throw new AbortException(e.getMessage());
        }

        throw new AbortException("failed to find zipalign: no zipalign/zipalign.exe in latest build-tools path " +
            zipalign.getParent().getRemote());
    }

    private final EnvVars env;
    private final FilePath workspace;
    private final PrintStream logger;
    private final String overrideAndroidHome;
    private final String overrideZipalignPath;
    private FilePath zipalign;

    ZipalignTool(@Nonnull EnvVars env, @Nonnull FilePath workspace, @Nonnull PrintStream logger, @Nullable String overrideAndroidHome, @Nullable String overrideZipalignPath) {
        this.env = env;
        this.workspace = workspace;
        this.logger = logger;
        this.overrideAndroidHome = overrideAndroidHome;
        this.overrideZipalignPath = overrideZipalignPath;
    }

    ArgumentListBuilder commandFor(String unsignedApk, String outputApk) throws AbortException {
        if (zipalign == null) {
            if (!StringUtils.isEmpty(overrideZipalignPath)) {
                logger.printf("[SignApksBuilder] zipalign path explicitly set to %s", overrideZipalignPath);
                zipalign = workspace.child(env.expand(overrideZipalignPath));
            }
            else if (!StringUtils.isEmpty(overrideAndroidHome)) {
                logger.printf("[SignApksBuilder] zipalign %s explicitly set to %s", ENV_ANDROID_HOME, overrideAndroidHome);
                String expandedAndroidHome = env.expand(overrideAndroidHome);
                zipalign = findInAndroidHome(expandedAndroidHome, workspace, this.logger);
            }
            else {
                zipalign = findFromEnv(env, workspace, logger);
            }

            zipalign = ensureZipalignExists(zipalign, logger);
        }

        return new ArgumentListBuilder()
            .add(zipalign.getRemote())
            .add("-f")
            .add("-p").add("4")
            .add(unsignedApk)
            .add(outputApk);
    }
}
