package org.jenkinsci.plugins.androidsigning;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.ArgumentListBuilder;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;


public class ZipalignToolTest {

    FilePath workspace;
    FilePath androidHome;
    FilePath androidHomeZipalign;
    FilePath altZipalign;

    @Before
    public void setUp() throws URISyntaxException {
        URL workspaceUrl = getClass().getResource("/workspace");
        workspace = new FilePath(new File(workspaceUrl.toURI()));
        URL androidHomeUrl = getClass().getResource("/android");
        androidHome = new FilePath(new File(androidHomeUrl.toURI()));
        androidHomeZipalign = androidHome.child("build-tools").child("1.0").child("zipalign");
        URL altZipalignUrl = getClass().getResource("/alt-zipalign");
        altZipalign = new FilePath(new File(altZipalignUrl.toURI())).child("zipalign");
    }

    @Test
    public void findsZipalignInAndroidHomeEnvVar() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(androidHomeZipalign.getRemote()));
    }

    @Test
    public void findsZipalignInAndroidZipalignEnvVar() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    public void androidZiplignOverridesAndroidHome() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(altZipalign.getRemote()));
    }

    @Test
    public void usesLatestZipalignFromAndroidHome() throws IOException, InterruptedException {
        FilePath newerBuildTools = androidHome.child("build-tools").child("1.1");
        newerBuildTools.mkdirs();
        FilePath newerZipalign = newerBuildTools.child("zipalign");
        newerZipalign.write("# fake zipalign", "utf-8");

        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(newerZipalign.getRemote()));

        newerBuildTools.deleteRecursive();
    }

    @Test
    public void explicitAndroidHomeOverridesEnvVars() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitAndroidHome = workspace.createTempDir("my-android-home", "");
        androidHome.copyRecursiveTo(explicitAndroidHome);

        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, explicitAndroidHome.getRemote(), null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitAndroidHome.getRemote()));

        explicitAndroidHome.deleteRecursive();
    }

    @Test
    public void explicitZipalignOverridesEnvZipaligns() throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitZipalign = workspace.createTempDir("my-zipalign", "").child("zipalign");
        explicitZipalign.write("# fake zipalign", "utf-8");
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, explicitZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitZipalign.getRemote()));

        explicitZipalign.getParent().deleteRecursive();
    }

    @Test
    public void explicitZipalignOverridesEverything() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, androidHomeZipalign.getRemote());
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, altZipalign.getRemote());

        FilePath explicitAndroidHome = workspace.createTempDir("my-android-home", "");
        androidHome.copyRecursiveTo(explicitAndroidHome);

        FilePath explicitZipalign = workspace.createTempDir("my-zipalign", "").child("zipalign");
        explicitZipalign.write("# fake zipalign", "utf-8");

        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, explicitAndroidHome.getRemote(), explicitZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(explicitZipalign.getRemote()));

        explicitZipalign.getParent().deleteRecursive();
        explicitAndroidHome.deleteRecursive();
    }

    @Test
    public void triesWindowsExeIfEnvAndroidHomeZipalignDoesNotExist() throws IOException, InterruptedException, URISyntaxException {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath winAndroidHomeZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");

        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ANDROID_HOME, winAndroidHome.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHomeZipalign.getRemote()));
    }

    @Test
    public void triesWindowsExeIfEnvZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath suffixedZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");
        FilePath unsuffixedZipalign = suffixedZipalign.getParent().child("zipalign");

        EnvVars envVars = new EnvVars();
        envVars.put(ZipalignTool.ENV_ZIPALIGN_PATH, unsuffixedZipalign.getRemote());
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(suffixedZipalign.getRemote()));
    }

    @Test
    public void triesWindowsExeIfExplicitAndroidHomeZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath winAndroidHomeZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");

        EnvVars envVars = new EnvVars();
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, winAndroidHome.getRemote(), null);
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(winAndroidHomeZipalign.getRemote()));
    }

    @Test
    public void triesWindowsExeIfExplicitZipalignDoesNotExist() throws Exception {
        URL androidHomeUrl = getClass().getResource("/win-android");
        FilePath winAndroidHome = new FilePath(new File(androidHomeUrl.toURI()));
        FilePath suffixedZipalign = winAndroidHome.child("build-tools").child("1.0").child("zipalign.exe");
        FilePath unsuffixedZipalign = suffixedZipalign.getParent().child("zipalign");

        EnvVars envVars = new EnvVars();
        ZipalignTool zipalign = new ZipalignTool(envVars, workspace, System.out, null, unsuffixedZipalign.getRemote());
        ArgumentListBuilder cmd = zipalign.commandFor("test.apk", "test-aligned.apk");

        assertThat(cmd.toString(), startsWith(suffixedZipalign.getRemote()));
    }

}
