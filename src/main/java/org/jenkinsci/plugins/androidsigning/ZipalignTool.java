package org.jenkinsci.plugins.androidsigning;

import org.apache.commons.lang.StringUtils;

import java.io.File;
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
    static final String ENV_PATH = "PATH";

    private static FilePath findFromEnv(EnvVars env, FilePath workspace, PrintStream logger) throws AbortException {

        String zipalignPath = env.get(ENV_ZIPALIGN_PATH);
        if (!StringUtils.isEmpty(zipalignPath)) {
            zipalignPath = env.expand(zipalignPath);
            logger.printf("[SignApksBuilder] found zipalign path in env %s=%s%n", ENV_ZIPALIGN_PATH, zipalignPath);
            FilePath zipalign = new FilePath(workspace.getChannel(), zipalignPath);
            return zipalignOrZipalignExe(zipalign, logger);
        }

        String androidHome = env.get(ENV_ANDROID_HOME);
        if (!StringUtils.isEmpty(androidHome)) {
            androidHome = env.expand(androidHome);
            logger.printf("[SignApksBuilder] searching environment variable %s=%s for zipalign...%n", ENV_ANDROID_HOME, androidHome);
            return findInAndroidHome(androidHome, workspace, logger);
        }

        String envPath = env.get(ENV_PATH);
        if (!StringUtils.isEmpty(envPath)) {
            envPath = env.expand(envPath);
            logger.printf("[SignApksBuilder] searching environment %s=%s for zipalign...%n", ENV_PATH, envPath);
            return findInPathEnvVar(envPath, workspace, logger);
        }

        throw new AbortException("failed to find zipalign: no environment variable " + ENV_ZIPALIGN_PATH + " or " + ENV_ANDROID_HOME + " or " + ENV_PATH);
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
        FilePath zipalign = zipalignOrZipalignExe(buildTools, logger);
        if (zipalign != null) {
            logger.printf("[SignApksBuilder] found zipalign in Android SDK's latest build tools: %s%n", zipalign.getRemote());
            return zipalign;
        }

        throw new AbortException("failed to find zipalign: no zipalign found in latest Android build tools: " + buildTools);
    }

    private static FilePath findInPathEnvVar(String envPath, FilePath workspace, PrintStream logger) throws AbortException {
        String[] dirs = envPath.split(File.pathSeparator);
        for (String dir : dirs) {
            FilePath dirPath = workspace.child(dir);
            FilePath zipalign = zipalignOrZipalignExe(dirPath, logger);
            if (zipalign != null) {
                return zipalign;
            }
            try {
                dirPath = androidHomeAncestorOfPath(dirPath);
            }
            catch (Exception e) {
                logger.println("error searching " + ENV_PATH + " environment variable: " + e.getMessage());
                e.printStackTrace(logger);
            }
            if (dirPath != null) {
                try {
                    return findInAndroidHome(dirPath.getRemote(), workspace, logger);
                }
                catch (AbortException e) {
                    logger.printf("error searching Android home found in " + ENV_PATH + ": " + e.getMessage());
                }
            }
        }

        return null;
    }

    private static FilePath androidHomeAncestorOfPath(FilePath path) throws Exception {
        if ("bin".equals(path.getName())) {
            FilePath sdkmanager = path.child("sdkmanager");
            if (sdkmanager.exists()) {
                path = path.getParent();
                if (path != null && "tools".equals(path.getName())) {
                    return path.getParent();
                }
            }
        }
        else if ("tools".equals(path.getName())) {
            FilePath androidTool = path.child("android");
            if (androidTool.exists()) {
                return path.getParent();
            }
        }
        else if (path.child("tools").child("android").exists()) {
            return path;
        }

        return null;
    }

    private static FilePath zipalignOrZipalignExe(FilePath zipalignOrDir, PrintStream logger) {
        try {
            if (zipalignOrDir.isDirectory()) {
                zipalignOrDir = zipalignOrDir.child("zipalign");
            }
        }
        catch (Exception e) {
            logger.println("[SignApksBuilder] error checking for zipalign at path " + zipalignOrDir);
            e.printStackTrace(logger);
        }
        try {
            if (zipalignOrDir.exists()) {
                return zipalignOrDir;
            }
        }
        catch (Exception e) {
            logger.println("[SignApksBuilder] error checking for zipalign at path " + zipalignOrDir);
            e.printStackTrace(logger);
        }
        try {
            zipalignOrDir = zipalignOrDir.getParent().child("zipalign.exe");
            if (zipalignOrDir.exists()) {
                return zipalignOrDir;
            }
        }
        catch (Exception e) {
            logger.println("[SignApksBuilder] error checking for zipalign.exe at path " + zipalignOrDir);
            e.printStackTrace(logger);
        }

        logger.println("[SignApksBuilder] no zipalign found at path " + zipalignOrDir);

        return null;
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
                logger.printf("[SignApksBuilder] zipalign path explicitly set to %s%n", overrideZipalignPath);
                zipalign = zipalignOrZipalignExe(workspace.child(env.expand(overrideZipalignPath)), logger);
            }
            else if (!StringUtils.isEmpty(overrideAndroidHome)) {
                logger.printf("[SignApksBuilder] zipalign %s explicitly set to %s%n", ENV_ANDROID_HOME, overrideAndroidHome);
                String expandedAndroidHome = env.expand(overrideAndroidHome);
                zipalign = findInAndroidHome(expandedAndroidHome, workspace, this.logger);
            }
            else {
                zipalign = findFromEnv(env, workspace, logger);
            }

            if (zipalign == null) {
                throw new AbortException("failed to find zipalign path in parameters or environment");
            }
        }

        return new ArgumentListBuilder()
            .add(zipalign.getRemote())
            .add("-f")
            .add("-p").add("4")
            .add(unsignedApk)
            .add(outputApk);
    }
}
