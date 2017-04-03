package org.jenkinsci.plugins.androidsigning;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.WithoutJenkins;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.util.VirtualFile;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.jenkinsci.plugins.androidsigning.TestKeyStore.KEY_ALIAS;
import static org.jenkinsci.plugins.androidsigning.TestKeyStore.KEY_STORE_ID;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class SignApksBuilderTest {

    private static BuildArtifact buildArtifact(FreeStyleBuild build, Run.Artifact artifact) {
        return new BuildArtifact(build, artifact);
    }

    private ApkArtifactIsSignedMatcher isSigned() throws KeyStoreException {
        return ApkArtifactIsSignedMatcher.isSignedWith(KEY_STORE_ID, KEY_ALIAS);
    }

    private FilePath androidHome = null;
    private FakeZipalign zipalignLauncher = null;
    private PretendSlave slave = null;
    private JenkinsRule testJenkins = new JenkinsRule();
    private TestKeyStore keyStoreRule = new TestKeyStore(testJenkins);

    @Rule
    public RuleChain jenkinsChain = RuleChain.outerRule(testJenkins).around(keyStoreRule);

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setupEnvironment() throws Exception {
        if (testJenkins.jenkins == null) {
            return;
        }
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        URL androidHomeUrl = getClass().getResource("/android");
        String androidHomePath = androidHomeUrl.getPath();
        envVars.put("ANDROID_HOME", androidHomePath);
        testJenkins.jenkins.getGlobalNodeProperties().add(prop);
        androidHome = new FilePath(new File(androidHomeUrl.toURI()));

        // add a slave so i can use my fake launcher
        zipalignLauncher = new FakeZipalign();
        slave = testJenkins.createPretendSlave(zipalignLauncher);
        slave.setLabelString(slave.getLabelString() + " " + getClass().getSimpleName());
    }

    private FreeStyleProject createSignApkJob() throws IOException {
        FreeStyleProject job = testJenkins.createFreeStyleProject(currentTestName.getMethodName());
        job.getBuildWrappersList().add(new CopyTestWorkspace());
        job.setAssignedLabel(Label.get(getClass().getSimpleName()));
        return job;
    }

    @Test
    @WithoutJenkins
    public void setsEmptyStringsToNullForAndroidHomeAndZipalignPath() {
        SignApksBuilder builder = new SignApksBuilder();

        builder.setAndroidHome("");
        assertThat(builder.getAndroidHome(), nullValue());
        builder.setAndroidHome(" ");
        assertThat(builder.getAndroidHome(), nullValue());

        builder.setZipalignPath("");
        assertThat(builder.getZipalignPath(), nullValue());
        builder.setZipalignPath(" ");
        assertThat(builder.getZipalignPath(), nullValue());
    }

    @Test
    public void credentailsExist() {
        List<StandardCertificateCredentials> result = CredentialsProvider.lookupCredentials(
            StandardCertificateCredentials.class, testJenkins.jenkins, ACL.SYSTEM, Collections.emptyList());
        StandardCertificateCredentials credentials = CredentialsMatchers.firstOrNull(result, CredentialsMatchers.withId(KEY_STORE_ID));
        assertThat(credentials, sameInstance(keyStoreRule.credentials));
        try {
            assertTrue(credentials.getKeyStore().containsAlias(KEY_ALIAS));
        }
        catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void archivesTheSignedApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
    }

    @Test
    public void archivesTheUnsignedApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(false).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-unsigned.apk"));
    }

    @Test
    public void archivesTheUnsignedAndSignedApks() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(2));
        Run.Artifact signedApkArtifact = artifacts.get(0);
        Run.Artifact unsignedApkArtifact = artifacts.get(1);
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-unsigned.apk"));
    }

    @Test
    public void archivesNothing() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk")
            .archiveSignedApks(false).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts, empty());
    }

    @Test
    public void signsTheApk() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        Run.Artifact signedApkArtifact = artifacts.get(0);

        assertThat(buildArtifact(build, signedApkArtifact), isSigned());
    }

    @Test
    public void supportsApksWithoutUnsignedSuffix() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "SignApksBuilderTest.apk")
            .archiveSignedApks(true).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        Run.Artifact signedApkArtifact = artifacts.get(0);
        Run.Artifact unsignedApkArtifact = artifacts.get(1);

        assertThat(buildArtifact(build, signedApkArtifact), isSigned());
        assertThat(signedApkArtifact.getFileName(), equalTo("SignApksBuilderTest-signed.apk"));
        assertThat(unsignedApkArtifact.getFileName(), equalTo("SignApksBuilderTest.apk"));
    }

    @Test
    public void signsAllMatchingApks() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "SignApksBuilderTest-*.apk")
            .archiveSignedApks(true).archiveUnsignedApk(true));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));
        assertThat(artifacts, hasItems(
            hasProperty("fileName", endsWith("SignApksBuilderTest-chocolate_flavor.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-chocolate_flavor-signed.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-unsigned.apk")),
            hasProperty("fileName", endsWith("SignApksBuilderTest-signed.apk"))));

        //noinspection Duplicates
        artifacts.forEach(artifact -> {
            try {
                if (!artifact.getFileName().endsWith("-signed.apk")) {
                    return;
                }
                assertThat(buildArtifact(build, artifact), isSigned());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void signsMultipleApksThatWillHaveConflictingSignedFileNames() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest.apk, SignApksBuilderTest-unsigned.apk");
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(true);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));

        List<String> relPaths = artifacts.stream().map(artifact -> artifact.relativePath).collect(Collectors.toList());
        assertThat(relPaths, hasItems(
            SignApksBuilder.BUILDER_DIR + "/" + KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest-unsigned.apk/SignApksBuilderTest-signed.apk",
            SignApksBuilder.BUILDER_DIR + "/" + KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest-unsigned.apk/SignApksBuilderTest-unsigned.apk",
            SignApksBuilder.BUILDER_DIR + "/" + KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest.apk/SignApksBuilderTest.apk",
            SignApksBuilder.BUILDER_DIR + "/" + KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest.apk/SignApksBuilderTest-signed.apk"));

        //noinspection Duplicates
        artifacts = artifacts.stream().filter(a -> a.getFileName().endsWith("-signed.apk")).collect(Collectors.toList());

        assertThat(artifacts.size(), equalTo(2));

        for (Run.Artifact artifact : artifacts) {
            assertThat(buildArtifact(build, artifact), isSigned());
        }
    }

    @Test
    public void multipleBuildersDoNotOverwriteArtifacts() throws Exception {
        SignApksBuilder builder1 = new SignApksBuilder();
        builder1.setKeyStoreId(KEY_STORE_ID);
        builder1.setKeyAlias(KEY_ALIAS);
        builder1.setApksToSign("SignApksBuilderTest.apk");
        builder1.setArchiveSignedApks(true);
        builder1.setArchiveUnsignedApks(true);

        SignApksBuilder builder2 = new SignApksBuilder();
        builder2.setKeyStoreId(KEY_STORE_ID);
        builder2.setKeyAlias(KEY_ALIAS);
        builder2.setApksToSign("SignApksBuilderTest-unsigned.apk");
        builder2.setArchiveSignedApks(true);
        builder2.setArchiveUnsignedApks(true);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder1);
        job.getBuildersList().add(builder2);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(4));

        VirtualFile archive = build.getArtifactManager().root();
        VirtualFile[] archiveDirs = archive.list();

        assertThat(archiveDirs.length, equalTo(1));

        VirtualFile archiveDir = archiveDirs[0];
        List<String> apkNames = Arrays.asList(archiveDir.list("**/*.apk"));
        assertThat(apkNames.size(), equalTo(4));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest.apk/SignApksBuilderTest.apk"));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest.apk/SignApksBuilderTest-signed.apk"));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest-unsigned.apk/SignApksBuilderTest-unsigned.apk"));
        assertThat(apkNames, hasItem(KEY_STORE_ID + "/" + KEY_ALIAS + "/SignApksBuilderTest-unsigned.apk/SignApksBuilderTest-signed.apk"));

        artifacts = artifacts.stream().filter(a -> a.getFileName().endsWith("-signed.apk")).collect(Collectors.toList());

        assertThat(artifacts.size(), equalTo(2));

        Run.Artifact bigger = artifacts.stream().filter(artifact ->
            artifact.getDisplayPath().endsWith("SignApksBuilderTest.apk/SignApksBuilderTest-signed.apk")).findFirst().get();
        Run.Artifact smaller = artifacts.stream().filter(artifact ->
            artifact.getDisplayPath().endsWith("SignApksBuilderTest-unsigned.apk/SignApksBuilderTest-signed.apk")).findFirst().get();

        assertThat(bigger.getFileSize(), greaterThan(smaller.getFileSize()));

        for (Run.Artifact artifact : artifacts) {
            assertThat(buildArtifact(build, artifact), isSigned());
        }
    }

    @Test
    public void writesSignedApkToUnsignedApkSibling() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest-unsigned.apk, standard_gradle_proj/**/*-release-unsigned.apk");
        builder.setSignedApkMapping(new SignedApkMappingStrategy.UnsignedApkSiblingMapping());
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(false);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(2));
        List<String> artifactNames = artifacts.stream().map(Run.Artifact::getFileName).collect(Collectors.toList());
        assertThat(artifactNames, everyItem(endsWith("-signed.apk")));

        FilePath workspace = build.getWorkspace();
        assertThat(workspace.child("SignApksBuilderTest-signed.apk").exists(), is(true));
        assertThat(workspace.child("standard_gradle_proj/app/build/outputs/apk/app-release-signed.apk").exists(), is(true));
    }

    @Test
    public void supportsMultipleApkGlobs() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest.apk, *chocolate*.apk, *-unsigned.apk");
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(false);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(3));
        List<String> artifactNames = artifacts.stream().map(Run.Artifact::getFileName).collect(Collectors.toList());
        assertThat(artifactNames, everyItem(endsWith("-signed.apk")));
    }

    @Test
    public void doesNotMatchTheSameApkMoreThanOnce() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setApksToSign("SignApksBuilderTest.apk, *Test.apk");
        builder.setArchiveSignedApks(true);
        builder.setArchiveUnsignedApks(false);

        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = testJenkins.buildAndAssertSuccess(job);
        List<Run<FreeStyleProject,FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();

        assertThat(artifacts.size(), equalTo(1));
    }

    @Test
    public void usesAndroidHomeOverride() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, getClass().getSimpleName(), "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FilePath androidHomeOverride = testJenkins.jenkins.getRootPath().createTempDir("android-home-override", null);
        androidHome.copyRecursiveTo(androidHomeOverride);
        builder.setAndroidHome(androidHomeOverride.getRemote());
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc.cmds().get(0), startsWith(androidHomeOverride.getRemote()));
    }

    @Test
    public void usesZipalignPathOverride() throws Exception {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false));
        SignApksBuilder builder = new SignApksBuilder(entries);
        FilePath zipalignOverride = testJenkins.jenkins.getRootPath().createTempDir("zipalign-override", null);
        zipalignOverride = zipalignOverride.createTextTempFile("zipalign-override", ".sh", "echo \"zipalign $@\"");
        builder.setZipalignPath(zipalignOverride.getRemote());
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc.cmds().get(0), startsWith(zipalignOverride.getRemote()));
    }

    @Test
    public void skipsZipalign() throws Exception {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setApksToSign("*-unsigned.apk");
        builder.setKeyStoreId(KEY_STORE_ID);
        builder.setKeyAlias(KEY_ALIAS);
        builder.setSkipZipalign(true);
        FreeStyleProject job = createSignApkJob();
        job.getBuildersList().add(builder);
        testJenkins.buildAndAssertSuccess(job);

        assertThat(zipalignLauncher.lastProc, nullValue());
    }

    @Test
    public void identitySubmission() throws Exception {
        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(KEY_ALIAS);
        original.setApksToSign("**/*-unsigned.apk");
        original.setSignedApkMapping(new SignedApkMappingStrategy.UnsignedApkSiblingMapping());
        original.setSkipZipalign(true);
        original.setArchiveSignedApks(!original.getArchiveSignedApks());
        original.setArchiveUnsignedApks(!original.getArchiveUnsignedApks());
        original.setAndroidHome(androidHome.getRemote());
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        testJenkins.submit(form);
        SignApksBuilder submitted = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getEntries(), nullValue());
        testJenkins.assertEqualBeans(original, submitted, String.join(",",
            "keyStoreId",
            "keyAlias",
            "apksToSign",
            "skipZipalign",
            "archiveUnsignedApks",
            "archiveSignedApks",
            "androidHome",
            "zipalignPath"
        ));
        assertThat(submitted.getSignedApkMapping(), instanceOf(original.getSignedApkMapping().getClass()));
    }

    @Test
    public void identitySubmissionWithSingleOldSigningEntry() throws Exception {
        Apk entry = new Apk(KEY_STORE_ID, KEY_ALIAS, "**/*-unsigned.apk")
            .archiveSignedApks(true).archiveUnsignedApk(false);
        SignApksBuilder original = new SignApksBuilder(Collections.singletonList(entry));
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        testJenkins.submit(form);
        SignApksBuilder submitted = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getEntries(), nullValue());
        testJenkins.assertEqualBeans(original, submitted, String.join(",",
            "keyStoreId",
            "keyAlias",
            "apksToSign",
            "skipZipalign",
            "archiveUnsignedApks",
            "archiveSignedApks",
            "androidHome",
            "zipalignPath"
        ));
        assertThat(submitted.getSignedApkMapping(), instanceOf(original.getSignedApkMapping().getClass()));
    }

    @Test
    public void descriptorProvidesKeyStoreFillMethod() throws Exception {

        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(KEY_ALIAS);
        original.setApksToSign("**/*-unsigned.apk");
        original.setArchiveSignedApks(!original.getArchiveSignedApks());
        original.setArchiveUnsignedApks(!original.getArchiveUnsignedApks());
        original.setAndroidHome(androidHome.getRemote());
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        // have to do this because Descriptor.calcFillSettings() fails outside the context of a Stapler web request
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        HtmlSelect keyStoreSelect = form.getSelectByName("_.keyStoreId");
        String fillUrl = keyStoreSelect.getAttribute("fillUrl");

        assertThat(fillUrl, not(isEmptyOrNullString()));

        HtmlOption option = keyStoreSelect.getOptionByValue(KEY_STORE_ID);

        assertThat(option, notNullValue());
        assertThat(option.getValueAttribute(), equalTo(KEY_STORE_ID));
    }

    @Test
    public void savesTheKeyStoreIdWithMultipleKeyStoresPresent() throws Exception {
        TestKeyStore otherKey = new TestKeyStore(testJenkins, "otherKey");
        otherKey.addCredentials();

        SignApksBuilder original = new SignApksBuilder();
        original.setKeyStoreId(KEY_STORE_ID);
        original.setKeyAlias(KEY_ALIAS);
        original.setApksToSign("**/*-unsigned.apk");
        original.setArchiveSignedApks(!original.getArchiveSignedApks());
        original.setArchiveUnsignedApks(!original.getArchiveUnsignedApks());
        original.setAndroidHome(androidHome.getRemote());
        FreeStyleProject job = testJenkins.createFreeStyleProject();
        job.getBuildersList().add(original);

        // have to do this because Descriptor.calcFillSettings() fails outside the context of a Stapler web request
        JenkinsRule.WebClient browser = testJenkins.createWebClient();
        HtmlPage configPage = browser.getPage(job, "configure");
        HtmlForm form = configPage.getFormByName("config");
        HtmlSelect keyStoreSelect = form.getSelectByName("_.keyStoreId");
        String fillUrl = keyStoreSelect.getAttribute("fillUrl");

        assertThat(fillUrl, not(isEmptyOrNullString()));

        HtmlOption option1 = keyStoreSelect.getOptionByValue(KEY_STORE_ID);
        HtmlOption option2 = keyStoreSelect.getOptionByValue(otherKey.credentialsId);

        assertThat(keyStoreSelect.getSelectedOptions().size(), equalTo(1));
        assertThat(keyStoreSelect.getSelectedOptions().get(0), equalTo(option1));

        keyStoreSelect.setSelectedIndex(keyStoreSelect.getOptions().indexOf(option2));

        testJenkins.submit(form);
        configPage = browser.getPage(job, "configure");
        form = configPage.getFormByName("config");
        keyStoreSelect = form.getSelectByName("_.keyStoreId");
        HtmlOption selectedOption = keyStoreSelect.getOptions().get(keyStoreSelect.getSelectedIndex());

        assertThat(selectedOption.getValueAttribute(), equalTo(otherKey.credentialsId));

        job = testJenkins.jenkins.getItemByFullName(job.getFullName(), FreeStyleProject.class);
        original = (SignApksBuilder) job.getBuildersList().get(0);

        assertThat(original.getKeyStoreId(), equalTo(otherKey.credentialsId));

        otherKey.removeCredentials();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void doesNotSupportMultipleEntriesAnyMore() {
        List<Apk> entries = new ArrayList<>();
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "ignore_me_1/**"));
        entries.add(new Apk(KEY_STORE_ID, KEY_ALIAS, "ignore_me_2/**"));
        SignApksBuilder builder = new SignApksBuilder(entries);
    }

}
